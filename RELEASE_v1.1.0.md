# MSDA v1.1.0 Release Notes

## Version 1.1.0 - QR Scanner & Account Management

### 🎉 New Features

#### 1. **QR Code Scanner for Steam Login**
- Real-time drag animation for intuitive user experience
- Drag left to progressively reveal delete button (50% threshold)
- Mobile-optimized Steam LoginV2 flow
- Portrait-locked scanner interface for consistent UX
- Automatic session handling with proper token management

#### 2. **Account Management Enhancements**
- **Swipe-to-Delete** - Smooth gesture-based account deletion
- Interactive account rows with real-time drag feedback
- Confirmation dialog to prevent accidental deletions
- Clean account removal from mafile storage

#### 3. **Backup & Export System**
- **Global Export** - Export all accounts as encrypted ZIP backup
- **Per-Account Export** - Export individual account mafile
- Secure sharing via Android chooser (Telegram, Email, Files, Cloud storage, etc.)
- Temporary file cleanup to prevent sensitive data leaks
- FileProvider for safe cross-app file sharing

#### 4. **Settings Improvements**
- GitHub repository link in settings footer (clickable, opens in browser)
- Centralized version management in gradle.properties
- Auto-generated APK naming based on version
- Version properly displayed in settings

### 🔧 Technical Improvements

- Automatic version management from gradle.properties
- Kotlin code warnings fixed and optimized
- Improved error handling for confirmation loading
- Removed unused parameters and assertions
- Secure temporary folder cleanup with try-finally
- Mobile-compatible header handling for Steam API calls

### 📝 Version Management

All version information is now centralized in `gradle.properties`:
- `app.version.name` - Semantic version (1.1.0)
- `app.version.code` - Integer version code (2)
- Automatically reflected in APK name and UI

To update version in future: Only modify `gradle.properties` and rebuild.

### 📱 UI/UX Enhancements

- Smooth animations for interactive elements
- Real-time touch feedback for account rows
- Improved visual affordances for swipe gestures
- Material Design compliance
- Dark and Light theme support maintained

### 🔒 Security

- Secure temporary file handling with immediate cleanup
- Encrypted backup support (mafiles remain encrypted in ZIP)
- FileProvider for safe file sharing
- No sensitive data left in cache after operations

### 📊 What Changed

- 24 files modified/created
- 1147 insertions
- 91 deletions
- New files: QrApprovalService.kt, file_paths.xml

### 🙏 Contributors

Developed with Visual Studio one-click F5 workflow for Android builds on emulator/device.

---

**Download:** Visit [Releases Page](https://github.com/bohdanbtw/MSDA/releases/tag/Release-stable)
