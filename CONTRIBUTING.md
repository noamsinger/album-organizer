# Contributing to Album Organizer

Thank you for your interest in contributing to Album Organizer! This document provides guidelines and instructions for contributing.

## Getting Started

### Prerequisites

- **Java Development Kit (JDK) 17 or higher**
- **Maven 3.6+**
- **Git**

### Setting Up Development Environment

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd album-organizer-v1
   ```

2. **Build the project**:
   ```bash
   ./build.sh        # On macOS/Linux
   build.bat         # On Windows
   ```

3. **Run the application**:
   ```bash
   mvn javafx:run
   ```

4. **Run tests**:
   ```bash
   mvn test
   ```

## Project Structure

```
album-organizer-v1/
├── src/main/java/com/albumorganizer/
│   ├── controller/      # UI controllers
│   ├── model/          # Domain models
│   ├── service/        # Business logic
│   ├── repository/     # Data persistence
│   ├── task/           # Background tasks
│   └── util/           # Utilities
├── src/main/resources/
│   └── com/albumorganizer/
│       ├── view/       # FXML layouts
│       └── styles.css  # CSS styling
├── src/test/java/      # Unit and integration tests
├── docs/               # Documentation
├── README.md
├── design.md
└── plan.md
```

## Development Guidelines

### Code Style

- **Java Code Style**: Follow standard Java conventions
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Maximum 120 characters
- **Naming**:
  - Classes: PascalCase (e.g., `MediaFile`)
  - Methods: camelCase (e.g., `calculateHash`)
  - Constants: UPPER_SNAKE_CASE (e.g., `HASH_ALGORITHM`)

### Logging

Use SLF4J with appropriate log levels:
- **ERROR**: System errors, exceptions
- **WARN**: Recoverable issues
- **INFO**: High-level operations
- **DEBUG**: Detailed diagnostic information

Example:
```java
private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
logger.info("Starting scan of {} folders", folderCount);
```

### Testing

- **Write tests** for all new features
- **Maintain test coverage** above 70%
- **Test types**:
  - Unit tests: Individual components
  - Integration tests: Component interactions
  - UI tests: User interface (TestFX)

Run tests:
```bash
mvn test                          # All tests
mvn test -Dtest=MyTest            # Specific test
mvn test -Dtest=MyTest#testMethod # Specific method
```

### Git Workflow

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** and commit:
   ```bash
   git add .
   git commit -m "Add feature: description"
   ```

3. **Keep commits atomic**: One logical change per commit

4. **Write clear commit messages**:
   ```
   Add support for RAW image formats
   
   - Added RAW extensions to Constants
   - Updated FileTypeDetector
   - Added tests for RAW detection
   ```

5. **Push your branch**:
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create a Pull Request**

### Pull Request Guidelines

- **Description**: Clearly describe what changes you made and why
- **Tests**: Include tests for new features
- **Documentation**: Update README.md or design.md if needed
- **Build**: Ensure `mvn clean package` succeeds
- **Review**: Address review comments promptly

## Areas for Contribution

### Feature Enhancements

- **Advanced Search**: Filter by date range, file size, resolution
- **Batch Operations**: Move, copy, or delete multiple files
- **Export**: Export results to CSV, JSON, or Excel
- **Cloud Storage**: Support for scanning cloud storage (optional)
- **Video Thumbnails**: Generate thumbnail previews
- **Face Detection**: AI-powered face detection in images

### Performance Improvements

- **Database Backend**: Replace INI files with SQLite
- **Incremental Indexing**: Background indexing
- **Memory Optimization**: Reduce memory footprint
- **Faster Hash Calculation**: GPU acceleration or alternative algorithms

### UI/UX Improvements

- **Dark Mode**: Add dark theme
- **Customizable Columns**: Allow users to show/hide columns
- **Saved Views**: Save filter/sort preferences
- **Timeline View**: Organize by date
- **Map View**: Show photos on a map (if GPS data available)

### Platform Support

- **Mobile Apps**: iOS/Android versions
- **Web Interface**: Browser-based version
- **CLI**: Command-line interface for automation

### Bug Fixes

Check the [Issues](../../issues) page for known bugs.

## Building Native Installers

### macOS (.dmg)

```bash
mvn clean package
mvn jpackage:jpackage
```

Output: `target/dist/AlbumOrganizer-1.0.0.dmg`

### Windows (.exe, .msi)

```bash
mvn clean package
mvn jpackage:jpackage
```

Output: `target/dist/AlbumOrganizer-1.0.0.exe` or `.msi`

### Linux (.deb, .rpm)

```bash
mvn clean package
mvn jpackage:jpackage
```

Output: `target/dist/album-organizer_1.0.0_amd64.deb` or `.rpm`

## Documentation

When adding features, update:
- **README.md**: User-facing documentation
- **design.md**: Technical architecture
- **plan.md**: Implementation details
- **JavaDoc**: Code documentation

## Testing Your Changes

### Manual Testing Checklist

Before submitting:
- [ ] Application launches successfully
- [ ] Can select folders
- [ ] Deep scan works and creates cache files
- [ ] Quick scan detects changes
- [ ] Files display in table with all columns
- [ ] Double-click opens files
- [ ] Context menus work
- [ ] Duplicate detection highlights correctly
- [ ] Progress dialog shows during scan
- [ ] No errors in console
- [ ] Tests pass: `mvn test`

### Cross-Platform Testing

If possible, test on:
- [ ] macOS (Intel and Apple Silicon)
- [ ] Windows 10/11
- [ ] Linux (Ubuntu/Fedora)

## Getting Help

- **Questions**: Open a [Discussion](../../discussions)
- **Bugs**: File an [Issue](../../issues)
- **Email**: [Maintainer email]

## Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on what is best for the project

## License

By contributing, you agree that your contributions will be licensed under the same license as the project.

---

Thank you for contributing! 🎉
