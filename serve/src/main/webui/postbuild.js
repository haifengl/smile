/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
import fs from 'node:fs';
import process from 'node:process';

// On Windows, directories created by Vite/esbuild can retain locked directory handles
// or metadata that cause Java's Files.move() in Quinoa to fail with AccessDeniedException.
// Recreating the dist directory cleanly via Node fs resolves this issue on Windows only.
if (process.platform === 'win32' && fs.existsSync('dist')) {
  fs.cpSync('dist', 'dist_tmp', { recursive: true });
  fs.rmSync('dist', { recursive: true, force: true });
  fs.renameSync('dist_tmp', 'dist');
}
