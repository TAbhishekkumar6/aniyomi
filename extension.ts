import * as vscode from 'vscode';
import { AndroidSDKManager } from './utils/AndroidSDKManager';

export async function activate(context: vscode.ExtensionContext) {
    try {
        // Check and install Android SDK if needed
        if (!AndroidSDKManager.isSDKInstalled()) {
            await AndroidSDKManager.installSDK();
        }
        
        // ...existing code...
    } catch (error: unknown) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        vscode.window.showErrorMessage(`Failed to initialize Android SDK: ${errorMessage}`);
    }
}

// ...existing code...
