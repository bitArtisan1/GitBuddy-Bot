# GitHub Bot

GitHub Bot is an automation tool designed to interact with GitHub's API. It allows you to automatically star repositories and follow users based on specific criteria. This tool ensures compliance with GitHub's Terms of Service by pacing its operations to avoid being flagged as spam.

## Features

- **Automatic Starring**: Automatically star repositories based on a set criteria (e.g., created in the last day, less than 4 stars).
- **Automatic Following**: Follow users who own the repositories that meet the criteria.
- **Undo Operations**: Unstar repositories and unfollow users to revert the operations.
- **Rate Limit Awareness**: Checks and respects GitHub API rate limits to avoid being throttled.
- **Graceful Shutdown**: Listens for shutdown signals to stop operations cleanly.
- **Slow-Paced Operations**: Introduces delays between operations to comply with GitHub's anti-spam policies.

## Working Principle

1. **Rate Limit Check**: Before starting operations, the bot checks the current rate limit to ensure it doesn't exceed the allowed number of requests.
2. **Fetch Repositories**: Searches for repositories created in the last day with less than 4 stars.
3. **Process Repositories**: Stars the repositories and follows the owners with a delay between each operation to avoid spam.
4. **Undo Operations**: Provides functionality to unstar repositories and unfollow users that were starred and followed during the bot's operations.

## Installation

1. **Clone the Repository**:
    ```sh
    git clone https://github.com/yourusername/github-bot.git
    cd github-bot
    ```

2. **Set Up Environment**:
    Ensure you have Java 11 or higher installed. Set the `GITHUB_TOKEN` environment variable with your GitHub Personal Access Token.
    ```sh
    export GITHUB_TOKEN=your_github_token
    ```

### Compiling in an IDE

#### Visual Studio Code

1. **Open Project**:
    - Open Visual Studio Code.
    - Open the cloned repository folder.

2. **Install Extensions**:
    - Install the Java Extension Pack from the Extensions view (`Ctrl+Shift+X`).

3. **Build and Run**:
    - Open the terminal in Visual Studio Code (`Ctrl+``).
    - Use the following command to build the project:
      ```sh
      ./gradlew build
      ```
    - Run the project using the command:
      ```sh
      java -jar build/libs/github-bot.jar
      ```

#### IntelliJ IDEA

1. **Open Project**:
    - Open IntelliJ IDEA.
    - Select "Open" and navigate to the cloned repository folder.

2. **Import Project**:
    - IntelliJ will detect the `build.gradle` file and prompt to import the project. Click "Import Gradle Project".

3. **Build and Run**:
    - Use the build tool window to execute `./gradlew build`.
    - Run the `GitHubBot` class by right-clicking on it and selecting "Run 'GitHubBot.main()'".

### Using the JAR Release

1. **Download the JAR**:
    - Go to the [Releases](https://github.com/yourusername/github-bot/releases) section of the repository.
    - Download the latest `.jar` release.

2. **Run the JAR**:
    ```sh
    java -jar path/to/github-bot.jar
    ```

## Usage

### Method 1: Building and Running

1. **Build the Project**:
    ```sh
    ./gradlew build
    ```

2. **Run the Bot**:
    ```sh
    java -jar build/libs/github-bot.jar
    ```

### Method 2: Running the Compiled `.jar` File

1. **Download the Compiled `.jar` File**:
    If you already have the compiled `.jar` file, you can directly run it using the following command:
    ```sh
    java -jar github-bot.jar
    ```

## Detailed Explanation

### Rate Limit Check

The bot first checks the rate limit using the GitHub API endpoint `/rate_limit`. This ensures that the bot does not exceed the allowed number of requests per hour.

### Fetch Repositories

The bot searches for repositories that were created in the last day and have less than 4 stars. This is done using the GitHub search API with a query parameter.

### Process Repositories

For each repository found, the bot:
1. **Stars the Repository**: Stars the repository using the GitHub API if it hasn't been starred before.
2. **Follows the User**: Follows the user who owns the repository if they haven't been followed before.

A delay of 5 seconds is introduced between each operation to avoid being flagged as spam.

### Undo Operations

The bot can undo its operations by un-starring repositories and unfollowing users. This is useful if you want to revert the actions performed by the bot.

### Graceful Shutdown

The bot listens for shutdown signals and stops its operations cleanly to ensure no incomplete actions are left.

## Contributing

If you would like to contribute to this project, please fork the repository and submit a pull request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.

## Support

For support or any questions, please open an issue in the repository or contact me at your.email@example.com.
