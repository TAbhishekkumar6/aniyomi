import * as fs from 'fs';
import * as path from 'path';
import * as https from 'https';
import * as child_process from 'child_process';
import { IncomingMessage } from 'http';

export class AndroidSDKManager {
    private static readonly ANDROID_HOME = process.env.ANDROID_HOME || this.getDefaultSDKPath();

    private static getDefaultSDKPath(): string {
        const home = process.env.HOME || process.env.USERPROFILE;
        return path.join(home!, 'Android', 'Sdk');
    }

    public static isSDKInstalled(): boolean {
        return fs.existsSync(this.ANDROID_HOME) &&
               fs.existsSync(path.join(this.ANDROID_HOME, 'tools')) &&
               fs.existsSync(path.join(this.ANDROID_HOME, 'platform-tools'));
    }

    public static async installSDK(): Promise<void> {
        if (this.isSDKInstalled()) {
            return;
        }

        console.log('Android SDK not found. Installing...');
        
        // Create SDK directory if it doesn't exist
        if (!fs.existsSync(this.ANDROID_HOME)) {
            fs.mkdirSync(this.ANDROID_HOME, { recursive: true });
        }

        // Download Command-line tools
        const cmdlineToolsUrl = this.getCmdlineToolsUrl();
        const downloadPath = path.join(this.ANDROID_HOME, 'cmdline-tools.zip');
        
        await this.downloadFile(cmdlineToolsUrl, downloadPath);
        await this.extractAndSetup(downloadPath);
        
        // Accept licenses and install required components
        await this.acceptLicenses();
        await this.installRequiredComponents();

        console.log('Android SDK installation completed successfully');
    }

    private static getCmdlineToolsUrl(): string {
        const platform = process.platform;
        switch (platform) {
            case 'win32':
                return 'https://dl.google.com/android/repository/commandlinetools-win-latest.zip';
            case 'darwin':
                return 'https://dl.google.com/android/repository/commandlinetools-mac-latest.zip';
            case 'linux':
                return 'https://dl.google.com/android/repository/commandlinetools-linux-latest.zip';
            default:
                throw new Error(`Unsupported platform: ${platform}`);
        }
    }

    private static downloadFile(url: string, dest: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const file = fs.createWriteStream(dest);
            https.get(url, (response: IncomingMessage) => {
                response.pipe(file);
                file.on('finish', () => {
                    file.close();
                    resolve();
                });
            }).on('error', (err: Error) => {
                fs.unlink(dest, () => {});
                reject(err);
            });
        });
    }

    private static async extractAndSetup(zipPath: string): Promise<void> {
        // Implementation depends on platform-specific unzip utility
        // This is a simplified example
        const unzipCommand = process.platform === 'win32' ? 
            `powershell -command "Expand-Archive -Path '${zipPath}' -DestinationPath '${this.ANDROID_HOME}'"` :
            `unzip -q '${zipPath}' -d '${this.ANDROID_HOME}'`;
        
        await this.executeCommand(unzipCommand);
        fs.unlinkSync(zipPath);
    }

    private static async acceptLicenses(): Promise<void> {
        const sdkmanager = path.join(this.ANDROID_HOME, 'cmdline-tools', 'latest', 'bin', 'sdkmanager');
        await this.executeCommand(`echo y | ${sdkmanager} --licenses`);
    }

    private static async installRequiredComponents(): Promise<void> {
        const sdkmanager = path.join(this.ANDROID_HOME, 'cmdline-tools', 'latest', 'bin', 'sdkmanager');
        const components = [
            'platform-tools',
            'platforms;android-33',
            'build-tools;33.0.0'
        ];
        
        for (const component of components) {
            await this.executeCommand(`${sdkmanager} "${component}"`);
        }
    }

    private static executeCommand(command: string): Promise<void> {
        return new Promise((resolve, reject) => {
            child_process.exec(command, (error: child_process.ExecException | null) => {
                if (error) {
                    reject(error);
                } else {
                    resolve();
                }
            });
        });
    }
}
