/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.io;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

/**
 * Parquet InputFile with a local java.nio.Path.
 * Adapted from https://github.com/tideworks/arvo2parquet
 */
class LocalInputFile implements InputFile {
    /** Local file object. */
    private final RandomAccessFile input;

    /** Constructor. */
    public LocalInputFile(Path path) throws FileNotFoundException {
        input = new RandomAccessFile(path.toFile(), "r");
    }

    @Override
    public long getLength() throws IOException {
        return input.length();
    }

    @Override
    public SeekableInputStream newStream() throws IOException {
        return new SeekableInputStream() {
            private final byte[] page = new byte[8192];
            private long markPos = 0;

            @Override
            public int read() throws IOException {
                return input.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return input.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return input.read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                final long savPos = input.getFilePointer();
                final long amtLeft = input.length() - savPos;
                n = Math.min(n, amtLeft);
                final long newPos = savPos + n;
                input.seek(newPos);
                final long curPos = input.getFilePointer();
                return curPos - savPos;
            }

            @Override
            public int available() throws IOException {
                return 0;
            }

            @Override
            public void close() throws IOException {
                input.close();
            }

            @SuppressWarnings({"unchecked", "unused", "UnusedReturnValue"})
            private <T extends Throwable, R> R uncheckedExceptionThrow(Throwable t) throws T {
                throw (T) t;
            }

            @Override
            public synchronized void mark(int readlimit) {
                try {
                    markPos = input.getFilePointer();
                } catch (IOException e) {
                    uncheckedExceptionThrow(e);
                }
            }

            @Override
            public synchronized void reset() throws IOException {
                input.seek(markPos);
            }

            @Override
            public boolean markSupported() {
                return true;
            }

            @Override
            public long getPos() throws IOException {
                return input.getFilePointer();
            }

            @Override
            public void seek(long l) throws IOException {
                input.seek(l);
            }

            @Override
            public void readFully(byte[] bytes) throws IOException {
                input.readFully(bytes);
            }

            @Override
            public void readFully(byte[] bytes, int i, int i1) throws IOException {
                input.readFully(bytes, i, i1);
            }

            @Override
            public int read(ByteBuffer byteBuffer) throws IOException {
                return readDirectBuffer(byteBuffer, page, input::read);
            }

            @Override
            public void readFully(ByteBuffer byteBuffer) throws IOException {
                readFullyDirectBuffer(byteBuffer, page, input::read);
            }
        };
    }

    private interface ByteBufferReader {
        int read(byte[] b, int off, int len) throws IOException;
    }

    private int readDirectBuffer(ByteBuffer byteBuffer, byte[] page, ByteBufferReader reader) throws IOException {
        // copy all the bytes that return immediately, stopping at the first
        // read that doesn't return a full buffer.
        int nextReadLength = Math.min(byteBuffer.remaining(), page.length);
        int totalBytesRead = 0;
        int bytesRead;

        while ((bytesRead = reader.read(page, 0, nextReadLength)) == page.length) {
            byteBuffer.put(page);
            totalBytesRead += bytesRead;
            nextReadLength = Math.min(byteBuffer.remaining(), page.length);
        }

        if (bytesRead < 0) {
            // return -1 if nothing was read
            return totalBytesRead == 0 ? -1 : totalBytesRead;
        } else {
            // copy the last partial buffer
            byteBuffer.put(page, 0, bytesRead);
            totalBytesRead += bytesRead;
            return totalBytesRead;
        }
    }

    private static void readFullyDirectBuffer(ByteBuffer byteBuffer, byte[] page, ByteBufferReader reader) throws IOException {
        int nextReadLength = Math.min(byteBuffer.remaining(), page.length);
        int bytesRead = 0;

        while (nextReadLength > 0 && (bytesRead = reader.read(page, 0, nextReadLength)) >= 0) {
            byteBuffer.put(page, 0, bytesRead);
            nextReadLength = Math.min(byteBuffer.remaining(), page.length);
        }

        if (bytesRead < 0 && byteBuffer.remaining() > 0) {
            throw new EOFException("Reached the end of stream with " + byteBuffer.remaining() + " bytes left to read");
        }
    }
}
