package io.github.merlin04.discordrpc;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Cross-platform Discord IPC client for Windows, Linux, and macOS
 */
public class SimpleDiscordIPC {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    
    // Windows API constants
    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int OPEN_EXISTING = 3;
    private static final int ERROR_NO_DATA = 232;
    private static final Pointer INVALID_HANDLE_VALUE = new Pointer(-1);
    
    // Unix constants
    private static final int AF_UNIX = 1;
    private static final int SOCK_STREAM = 1;
    
    // Direct Kernel32 interface (Windows)
    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        
        Pointer CreateFileA(String lpFileName, int dwDesiredAccess, int dwShareMode,
                          Pointer lpSecurityAttributes, int dwCreationDisposition,
                          int dwFlagsAndAttributes, Pointer hTemplateFile);
        
        boolean ReadFile(Pointer hFile, byte[] lpBuffer, int nNumberOfBytesToRead,
                        IntByReference lpNumberOfBytesRead, Pointer lpOverlapped);
        
        boolean WriteFile(Pointer hFile, byte[] lpBuffer, int nNumberOfBytesToWrite,
                         IntByReference lpNumberOfBytesWritten, Pointer lpOverlapped);
        
        boolean CloseHandle(Pointer hObject);
        
        int GetLastError();
    }
    
    // Direct libc interface (Linux/macOS)
    private interface LibC extends Library {
        LibC INSTANCE = Native.load("c", LibC.class);
        
        int socket(int domain, int type, int protocol);
        int connect(int sockfd, Pointer addr, int addrlen);
        int read(int fd, byte[] buf, int count);
        int write(int fd, byte[] buf, int count);
        int close(int fd);
    }
    
    private final long clientId;
    private boolean connected = false;
    
    // Platform-specific handles
    private Pointer windowsPipe;  // Windows
    private int unixSocket = -1;  // Linux/macOS
    
    public SimpleDiscordIPC(long clientId) {
        this.clientId = clientId;
    }
    
    public void connect() throws IOException {
        if (Platform.isWindows()) {
            connectWindows();
        } else if (Platform.isLinux() || Platform.isMac()) {
            connectUnix();
        } else {
            throw new IOException("Unsupported platform: " + System.getProperty("os.name"));
        }
    }
    
    private void connectWindows() throws IOException {
        Kernel32 kernel32 = Kernel32.INSTANCE;
        
        // Try to connect to Discord's named pipe
        for (int i = 0; i < 10; i++) {
            String pipeName = "\\\\.\\pipe\\discord-ipc-" + i;
            
            windowsPipe = kernel32.CreateFileA(
                pipeName,
                GENERIC_READ | GENERIC_WRITE,
                0,
                null,
                OPEN_EXISTING,
                0,
                null
            );
            
            if (!windowsPipe.equals(INVALID_HANDLE_VALUE)) {
                // Connected!
                String handshake = "{\"v\":1,\"client_id\":\"" + clientId + "\"}";
                send(OP_HANDSHAKE, handshake);
                connected = true;
                
                try {
                    read();
                } catch (Exception e) {
                    // Ignore handshake response
                }
                
                return;
            }
        }
        
        throw new IOException("Could not connect to Discord. Is Discord running?");
    }
    
    private void connectUnix() throws IOException {
        LibC libc = LibC.INSTANCE;
        
        // Get the appropriate runtime directory
        String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
        if (runtimeDir == null || runtimeDir.isEmpty()) {
            runtimeDir = System.getenv("TMPDIR");
            if (runtimeDir == null || runtimeDir.isEmpty()) {
                runtimeDir = System.getenv("TMP");
                if (runtimeDir == null || runtimeDir.isEmpty()) {
                    runtimeDir = System.getenv("TEMP");
                    if (runtimeDir == null || runtimeDir.isEmpty()) {
                        runtimeDir = "/tmp";
                    }
                }
            }
        }
        
        // Try to connect to Discord's Unix socket
        for (int i = 0; i < 10; i++) {
            String socketPath = runtimeDir + "/discord-ipc-" + i;
            
            unixSocket = libc.socket(AF_UNIX, SOCK_STREAM, 0);
            if (unixSocket < 0) {
                continue;
            }
            
            // Create sockaddr_un structure
            // struct sockaddr_un { sa_family_t sun_family; char sun_path[108]; }
            byte[] sockaddr = new byte[110];
            sockaddr[0] = (byte) AF_UNIX; // sun_family (2 bytes on most systems)
            sockaddr[1] = 0;
            byte[] pathBytes = socketPath.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(pathBytes, 0, sockaddr, 2, Math.min(pathBytes.length, 107));
            
            Pointer sockaddrPtr = new Pointer(Native.malloc(sockaddr.length));
            sockaddrPtr.write(0, sockaddr, 0, sockaddr.length);
            
            int result = libc.connect(unixSocket, sockaddrPtr, sockaddr.length);
            Native.free(Pointer.nativeValue(sockaddrPtr));
            
            if (result == 0) {
                // Connected!
                String handshake = "{\"v\":1,\"client_id\":\"" + clientId + "\"}";
                send(OP_HANDSHAKE, handshake);
                connected = true;
                
                try {
                    read();
                } catch (Exception e) {
                    // Ignore handshake response
                }
                
                return;
            } else {
                libc.close(unixSocket);
                unixSocket = -1;
            }
        }
        
        throw new IOException("Could not connect to Discord. Is Discord running?");
    }
    
    public void updatePresence(String details, String state, String largeImage, String largeImageText, long startTimestamp) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to Discord");
        }
        
        StringBuilder json = new StringBuilder();
        json.append("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":").append(getPid());
        json.append(",\"activity\":{");
        
        if (details != null) {
            json.append("\"details\":\"").append(escapeJson(details)).append("\",");
        }
        if (state != null) {
            json.append("\"state\":\"").append(escapeJson(state)).append("\",");
        }
        if (largeImage != null) {
            json.append("\"assets\":{\"large_image\":\"").append(escapeJson(largeImage)).append("\"");
            if (largeImageText != null) {
                json.append(",\"large_text\":\"").append(escapeJson(largeImageText)).append("\"");
            }
            json.append("},");
        }
        if (startTimestamp > 0) {
            json.append("\"timestamps\":{\"start\":").append(startTimestamp).append("},");
        }
        
        // Remove trailing comma if present
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        
        json.append("}},\"nonce\":\"").append(System.currentTimeMillis()).append("\"}");
        
        send(OP_FRAME, json.toString());
    }
    
    public void close() {
        if (Platform.isWindows()) {
            if (windowsPipe != null && !windowsPipe.equals(INVALID_HANDLE_VALUE)) {
                Kernel32.INSTANCE.CloseHandle(windowsPipe);
                windowsPipe = null;
            }
        } else {
            if (unixSocket >= 0) {
                LibC.INSTANCE.close(unixSocket);
                unixSocket = -1;
            }
        }
        connected = false;
    }
    
    private void send(int op, String data) throws IOException {
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        
        ByteBuffer buffer = ByteBuffer.allocate(8 + dataBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(op);
        buffer.putInt(dataBytes.length);
        buffer.put(dataBytes);
        
        if (Platform.isWindows()) {
            IntByReference written = new IntByReference();
            boolean success = Kernel32.INSTANCE.WriteFile(
                windowsPipe,
                buffer.array(),
                buffer.capacity(),
                written,
                null
            );
            
            if (!success || written.getValue() != buffer.capacity()) {
                throw new IOException("Failed to write to pipe");
            }
        } else {
            int written = LibC.INSTANCE.write(unixSocket, buffer.array(), buffer.capacity());
            if (written != buffer.capacity()) {
                throw new IOException("Failed to write to socket");
            }
        }
    }
    
    private String read() throws IOException {
        byte[] header = new byte[8];
        
        if (Platform.isWindows()) {
            IntByReference read = new IntByReference();
            boolean success = Kernel32.INSTANCE.ReadFile(
                windowsPipe,
                header,
                8,
                read,
                null
            );
            
            if (!success) {
                int error = Kernel32.INSTANCE.GetLastError();
                if (error == ERROR_NO_DATA) {
                    return null;
                }
                throw new IOException("Failed to read from pipe, error: " + error);
            }
            
            if (read.getValue() != 8) {
                return null;
            }
        } else {
            int bytesRead = LibC.INSTANCE.read(unixSocket, header, 8);
            if (bytesRead != 8) {
                return null;
            }
        }
        
        ByteBuffer headerBuf = ByteBuffer.wrap(header);
        headerBuf.order(ByteOrder.LITTLE_ENDIAN);
        int op = headerBuf.getInt();
        int length = headerBuf.getInt();
        
        if (length > 0) {
            byte[] data = new byte[length];
            
            if (Platform.isWindows()) {
                IntByReference read = new IntByReference();
                boolean success = Kernel32.INSTANCE.ReadFile(
                    windowsPipe,
                    data,
                    length,
                    read,
                    null
                );
                
                if (success && read.getValue() == length) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            } else {
                int bytesRead = LibC.INSTANCE.read(unixSocket, data, length);
                if (bytesRead == length) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            }
        }
        
        return null;
    }
    
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private long getPid() {
        try {
            return ProcessHandle.current().pid();
        } catch (Exception e) {
            return -1;
        }
    }
}
