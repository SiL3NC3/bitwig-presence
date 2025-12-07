# Bitwig Studio Discord Rich Presence

Discord Rich Presence integration for Bitwig Studio - shows what project you're working on, what panel you're in (Arrange/Mix/Edit), and displays your Bitwig version directly in Discord.

![Activity screenshot](./activity-screenshot.png)

## ✨ Features

- 🎹 **Project Name Display** - Shows currently open project
- 🎚️ **Panel Detection** - Displays "Arranging", "Mixing", or "Editing"
- 🏷️ **Version Badge** - Shows Bitwig version in status (e.g. "Arranging • Bitwig 6")
- ⏱️ **Session Timer** - Tracks how long you've been working
- 🌍 **Cross-Platform** - Works on Windows, Linux, and macOS
- ⚡ **Efficient** - Event-based updates with 5-second rate limiting
- 🔧 **Easy Toggle** - Enable/disable from Bitwig settings

## 🎯 Compatibility

- ✅ **Bitwig Studio 6.x** (Beta/Stable)
- ✅ **Bitwig Studio 5.x**
- ⚠️ **Bitwig Studio 4.x** (untested but should work)

## 📦 Installation

### Download Release

1. Download the `.bwextension` file from the [releases page](https://github.com/SiL3NC3/bitwig-presence/releases)
2. Copy it to your Bitwig Extensions folder:
   - **Windows:** `%USERPROFILE%\Documents\Bitwig Studio\Extensions\`
   - **Linux:** `~/.BitwigStudio/Extensions/`
   - **macOS:** `~/Documents/Bitwig Studio/Extensions/`
3. Restart Bitwig Studio
4. Go to `Settings → Controllers` and add the controller
5. Find **Discord / Discord RPC** and enable it

### Build from Source

```bash
# Clone the repository
git clone https://github.com/SiL3NC3/bitwig-presence.git
cd bitwig-presence

# Build with Maven
mvn clean package

# Copy to Bitwig Extensions folder
# Windows:
copy target\DiscordRPC.bwextension %USERPROFILE%\Documents\Bitwig Studio\Extensions\

# Linux/macOS:
cp target/DiscordRPC.bwextension ~/.BitwigStudio/Extensions/
# or
cp target/DiscordRPC.bwextension ~/Documents/Bitwig\ Studio/Extensions/
```

## 🎮 Usage

After installation, a keyboard icon appears in the upper right corner of Bitwig. Click it to:

- **Toggle the extension on/off**
- View connection status

The extension will automatically:

- Connect to Discord when you open a project
- Update when you switch panels (Arrange/Mix/Edit)
- Disconnect when you close the project
- Show your Bitwig version in the status line

## 🔧 Technical Details

### Architecture

- **Pure Java IPC Implementation** - No native `.dll` dependencies
- **JNA-based** - Direct Windows/Unix API calls
- **Cross-Platform IPC:**
  - Windows: Named Pipes (`\\.\pipe\discord-ipc-X`)
  - Linux/macOS: Unix Domain Sockets (`$XDG_RUNTIME_DIR/discord-ipc-X`)
- **Rate Limiting** - Max 1 update per 5 seconds to reduce API spam

### Dependencies

- `net.java.dev.jna:jna:5.13.0` (core only, ~1.8 MB)
- `com.bitwig:extension-api:18` (provided by Bitwig)

### Discord Application

Application ID: `1306910213659561984`

## 🙏 Credits

**Original Author:** [Merlin04](https://github.com/Merlin04/bitwig-presence)  
A huge thank you to Merlin for creating the original extension and making it open source!

**This Fork:**

- Bitwig Studio 6 support
- Cross-platform compatibility (Windows/Linux/macOS)
- Version display integration
- Custom IPC implementation
- Minimized logging
- Rate limiting optimization

## 📝 License

MIT License - See [LICENSE](LICENSE) file for details

## 🐛 Issues & Contributions

Found a bug or have a feature request? Please open an issue on the [GitHub repository](https://github.com/yourusername/bitwig-presence/issues).

Pull requests are welcome!

## 🔗 Links

- [Original Repository](https://github.com/Merlin04/bitwig-presence)
- [Bitwig Studio](https://www.bitwig.com/)
- [Discord Rich Presence Documentation](https://discord.com/developers/docs/rich-presence/overview)
