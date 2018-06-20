// @flow

import fs from 'fs';
import path from 'path';
import { promisify } from 'util';
import { getSystemTemporaryDirectory } from './tempdir';

const fsReadFileAsync = promisify(fs.readFile);

const POLL_INTERVAL = 200;

const appDirectoryName = 'Mullvad VPN';

export type RpcCredentials = {
  connectionString: string,
  sharedSecret: string,
};

export class RpcAddressFile {
  _filePath: string;
  _pollIntervalId: ?IntervalID;
  _pollPromise: ?Promise<void>;

  constructor() {
    this._filePath = getRpcAddressFilePath();
  }

  get filePath(): string {
    return this._filePath;
  }

  poll(): Promise<void> {
    let promise = this._pollPromise;

    if (!promise) {
      promise = new Promise((resolve, _reject) => {
        const timer = setInterval(() => {
          fs.exists(this._filePath, (exists) => {
            if (exists) {
              clearInterval(timer);
              resolve();

              this._pollPromise = null;
            }
          });
        }, POLL_INTERVAL);
      });

      this._pollPromise = promise;
    }

    return promise;
  }

  isTrusted() {
    const filePath = this._filePath;
    switch (process.platform) {
      case 'win32':
        return isOwnedByLocalSystem(filePath);
      case 'darwin':
      case 'linux':
        return isOwnedAndOnlyWritableByRoot(filePath);
      default:
        throw new Error(`Unknown platform: ${process.platform}`);
    }
  }

  async waitUntilExists(): Promise<RpcCredentials> {
    const data = await fsReadFileAsync(this._filePath, 'utf8');
    const [connectionString, sharedSecret] = data.split('\n', 2);

    if (connectionString && sharedSecret !== undefined) {
      return {
        connectionString,
        sharedSecret,
      };
    } else {
      throw new Error('Cannot parse the RPC address file');
    }
  }
}

function getRpcAddressFilePath() {
  const rpcAddressFileName = '.mullvad_rpc_address';

  switch (process.platform) {
    case 'win32': {
      // Windows: %ALLUSERSPROFILE%\{appname}
      const programDataDirectory = process.env.ALLUSERSPROFILE;
      if (programDataDirectory) {
        const appDataDirectory = path.join(programDataDirectory, appDirectoryName);
        return path.join(appDataDirectory, rpcAddressFileName);
      } else {
        throw new Error('Missing %ALLUSERSPROFILE% environment variable');
      }
    }
    default:
      return path.join(getSystemTemporaryDirectory(), rpcAddressFileName);
  }
}

function isOwnedAndOnlyWritableByRoot(path: string): boolean {
  const stat = fs.statSync(path);
  const isOwnedByRoot = stat.uid === 0;
  const isOnlyWritableByOwner = (stat.mode & parseInt('022', 8)) === 0;

  return isOwnedByRoot && isOnlyWritableByOwner;
}

function isOwnedByLocalSystem(path: string): boolean {
  // $FlowFixMe: this module is only available on Windows
  const winsec = require('windows-security');
  const ownerSid = winsec.getFileOwnerSid(path, null);
  const isWellKnownSid = winsec.isWellKnownSid(
    ownerSid,
    winsec.WellKnownSid.BuiltinAdministratorsSid,
  );

  return isWellKnownSid;
}
