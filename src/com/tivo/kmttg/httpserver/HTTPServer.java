package com.tivo.kmttg.httpserver;

/*
 *  Copyright © 2005-2012 Amichai Rothman
 *
 *  This file is part of JLHTTP - the Java Lightweight HTTP Server.
 *
 *  JLHTTP is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  JLHTTP is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with JLHTTP.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  For additional info see http://www.freeutils.net/source/jlhttp/
 */

import java.io.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.*;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

import com.tivo.kmttg.main.config;

/**
 * The {@code HTTPServer} class implements a light-weight HTTP server.
 *
 * This server is 'conditionally compliant' with RFC 2616 ("Hypertext
 * Transfer Protocol -- HTTP/1.1"), which means it supports all functionality
 * required by the RFC, as well as some of the optional functionality.
 * Among the features are virtual hosts, partial content (i.e. download
 * continuation), file-based serving, automatic directory index generation,
 * GET/HEAD/POST/OPTIONS/TRACE method support, multiple contexts per host,
 * file upload support and more.
 *
 * This server is multithreaded in its support for multiple concurrent HTTP
 * connections, however its constituent classes are not thread-safe and require
 * external synchronization if accessed by multiple threads concurrently.
 *
 * This server is intentionally written as a single source file, in order
 * to make it as easy as possible to integrate into any existing project - by
 * simply adding this single file to the project sources. It does, however,
 * aim to maintain a structured and flexible design. There are no external
 * package dependencies.
 *
 * This file contains elaborate documentation of its classes and methods, as
 * well as implementation details and references to specific RFC sections
 * which clarify the logic behind the code. It is recommended that anyone
 * attempting to modify the protocol-level functionality become acquainted with
 * the RFC, in order to make sure that protocol compliance is not broken.
 *
 * @author Amichai Rothman
 * @since  2008-07-24
 */
public class HTTPServer {

    /**
     * The SimpleDateFormat-compatible formats of dates which must be supported.
     * Note that all generated date fields must be in the RFC 1123 format only,
     * while the others are supported by recipients for backwards-compatibility.
     */
    public static final String[] DATE_PATTERNS = {
        "EEE, dd MMM yyyy HH:mm:ss Z", // RFC 822, updated by RFC 1123
        "EEEE, dd-MMM-yy HH:mm:ss Z",  // RFC 850, obsoleted by RFC 1036
        "EEE MMM d HH:mm:ss yyyy"      // ANSI C's asctime() format
    };

    /**
     * A convenience array containing the carriage-return and line feed chars.
     */
    public static final byte[] CRLF = { 0x0d, 0x0a };

    /**
     * The HTTP status description strings.
     */
    protected static final String[] statuses = new String[600];

    static {
        // initialize status descriptions lookup table
        Arrays.fill(statuses, "Unknown Status");
        statuses[100] = "Continue";
        statuses[200] = "OK";
        statuses[204] = "No Content";
        statuses[206] = "Partial Content";
        statuses[301] = "Moved Permanently";
        statuses[302] = "Found";
        statuses[304] = "Not Modified";
        statuses[307] = "Temporary Redirect";
        statuses[400] = "Bad Request";
        statuses[401] = "Unauthorized";
        statuses[403] = "Forbidden";
        statuses[404] = "Not Found";
        statuses[412] = "Precondition Failed";
        statuses[413] = "Request Entity Too Large";
        statuses[414] = "Request-URI Too Large";
        statuses[416] = "Requested Range Not Satisfiable";
        statuses[417] = "Expectation Failed";
        statuses[500] = "Internal Server Error";
        statuses[501] = "Not Implemented";
        statuses[502] = "Bad Gateway";
        statuses[503] = "Service Unavailable";
        statuses[504] = "Gateway Time-out";
    }

    /**
     * A mapping of path suffixes (e.g. file extensions) to their corresponding
     * MIME types.
     */
    protected static final Map<String, String> contentTypes =
        new ConcurrentHashMap<String, String>();

    static {
        // add some default common content types
        // see http://www.iana.org/assignments/media-types/ for full list
        addContentType("application/java-archive", "jar");
        addContentType("application/javascript", "js");
        addContentType("application/json", "json");
        addContentType("application/msword", "doc");
        addContentType("application/octet-stream", "exe");
        addContentType("application/pdf", "pdf");
        addContentType("application/vnd.ms-excel", "xls");
        addContentType("application/vnd.ms-powerpoint", "ppt");
        addContentType("application/x-compressed", "tgz");
        addContentType("application/x-gzip", "gz");
        addContentType("application/x-tar", "tar");
        addContentType("application/xhtml+xml", "xhtml");
        addContentType("application/zip", "zip");
        
        // moyekj added these
        addContentType("application/x-mpegurl", "m3u8");
        addContentType("video/webm", "webm");
        
        addContentType("audio/mpeg", "mp3");
        addContentType("image/gif", "gif");
        addContentType("image/jpeg", "jpg", "jpeg");
        addContentType("image/png", "png");
        addContentType("image/svg+xml", "svg");
        addContentType("image/x-icon", "ico");
        addContentType("text/css", "css");
        addContentType("text/html; charset=utf-8", "htm", "html");
        addContentType("text/plain", "txt", "text", "log");
        addContentType("text/xml", "xml");
    }

    /**
     * The {@code LimitedInputStream} provides access to a limited number
     * of consecutive bytes from the underlying InputStream, starting at its
     * current position. If this limit is reached, it behaves as though the end
     * of stream has been reached (although the underlying stream remains open
     * and may contain additional data).
     */
    public static class LimitedInputStream extends FilterInputStream {

        protected long limit; // decremented when read, until it reaches zero
        protected boolean prematureEndException;

        /**
         * Constructs a LimitedInputStream with the given underlying
         * input stream and limit.
         *
         * @param in the underlying input stream
         * @param limit the maximum number of bytes that may be consumed from
         *        the underlying stream before this stream ends. If zero or
         *        negative, this stream will be at its end from initialization.
         * @param prematureEndException specifies the stream's behavior when
         *        the underlying stream end is reached before the limit is
         *        reached: if true, an exception is thrown, otherwise this
         *        stream reaches its end as well (i.e. read() returns -1)
         * @throws NullPointerException if the given stream is null
         */
        public LimitedInputStream(InputStream in, long limit,
                boolean prematureEndException) {
            super(in);
            if (in == null)
                throw new NullPointerException("input stream is null");
            this.limit = limit < 0 ? 0 : limit;
            this.prematureEndException = prematureEndException;
        }

        @Override
        public int read() throws IOException {
            int res = limit == 0 ? -1 : in.read();
            if (res == -1 && limit > 0 && prematureEndException)
                throw new IOException("unexpected end of stream");
            limit = res == -1 ? 0 : limit - 1;
            return res;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            int res = limit == 0 ? -1
                : in.read(b, off, len > limit ? (int)limit : len);
            if (res == -1 && limit > 0 && prematureEndException)
                throw new IOException("unexpected end of stream");
            limit = res == -1 ? 0 : limit - res;
            return res;
        }

        @Override
        public long skip(long n) throws IOException {
            long res = in.skip(n > limit ? limit : n);
            limit -= res;
            return res;
        }

        @Override
        public int available() throws IOException {
            int res = in.available();
            return res > limit ? (int)limit : res;
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }

    /**
     * The {@code ChunkedInputStream} decodes an InputStream whose data has the
     * "chunked" transfer encoding applied to it, providing the underlying data.
     */
    public static class ChunkedInputStream extends LimitedInputStream {

        protected Headers headers;
        protected boolean initialized;

        /**
         * Constructs a ChunkedInputStream with the given underlying stream, and
         * a headers container to which the stream's trailing headers will be
         * added.
         *
         * @param in the underlying "chunked"-encoded input stream
         * @param headers the headers container to which the stream's trailing
         *        headers will be added, or null if they are to be discarded
         * @throws NullPointerException if the given stream is null
         */
        public ChunkedInputStream(InputStream in, Headers headers) {
            super(in, 0, true);
            this.headers = headers;
        }

        @Override
        public int read() throws IOException {
            return limit <= 0 && initChunk() < 0 ? -1 : super.read();
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            return limit <= 0 && initChunk() < 0 ? -1 : super.read(b, off, len);
        }

        /**
         * Initializes the next chunk. If the previous chunk has not yet
         * ended, or the end of stream has been reached, does nothing.
         *
         * @return the length of the chunk, or -1 if the end of stream
         *         has been reached
         * @throws IOException if an IO error occurs or the stream is corrupt
         */
        protected long initChunk() throws IOException {
            if (limit == 0) { // finished previous chunk
                // read chunk-terminating CRLF if it's not the first chunk
                if (initialized) {
                    if (readLine(in).length() > 0)
                        throw new IOException("chunk data must end with CRLF");
                } else {
                    initialized = true;
                }
                limit = parseChunkSize(readLine(in)); // read next chunk size
                if (limit == 0) { // last chunk has size 0
                    limit = -1; // mark end of stream
                    // read trailing headers, if any
                    Headers trailingHeaders = readHeaders(in);
                    if (headers != null)
                        headers.addAll(trailingHeaders);
                }
            }
            return limit;
        }

        /**
         * Parses a chunk-size line.
         *
         * @param line the chunk-size line to parse
         * @return the chunk size
         * @throws IllegalArgumentException if the chunk-size line is invalid
         */
        protected static long parseChunkSize(String line)
                throws IllegalArgumentException {
            int pos = line.indexOf(';');
            if (pos > -1)
                line = line.substring(0, pos); // ignore params, if any
            try {
                return parseULong(line, 16); // throws NFE
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(
                    "invalid chunk size line: \"" + line + "\"");
            }
        }
    }

    /**
     * The {@code ChunkedOutputStream} encodes an OutputStream with the
     * "chunked" transfer encoding. It should be used only when the content
     * length is not known in advance, and with the response Transfer-Encoding
     * header set to "chunked".
     * <p>
     * Data is written to the stream by invocations of the {@link #initChunk}
     * method, each followed by writing to the stream exactly the specified
     * number of data bytes for that chunk. The {@link #writeChunk} method can
     * be used to do this using a single method call. To end the stream, the
     * {@link #writeTrailingChunk} method must be called.
     */
    public static class ChunkedOutputStream extends FilterOutputStream {

        protected int state; // the current stream state

        /**
         * Constructs a ChunkedOutputStream with the given underlying stream.
         *
         * @param out the underlying output stream to which the chunked stream
         *        is written.
         * @throws NullPointerException if the given stream is null
         */
        public ChunkedOutputStream(OutputStream out) {
            super(out);
            if (out == null)
                throw new NullPointerException("output stream is null");
        }

        /**
         * Initializes a new chunk with the given size.
         *
         * @param size the chunk size (must be positive)
         * @throws IllegalArgumentException if size is negative
         * @throws IOException if an IO error occurs, or the stream has
         *         already been ended
         */
        public void initChunk(long size) throws IOException {
            if (size < 0)
                throw new IllegalArgumentException("invalid size: " + size);
            if (state > 0)
                out.write(CRLF); // end previous chunk
            else if (state == 0)
                state = 1; // start first chunk
            else if (state < 0)
                throw new IOException("chunked stream has already ended");
            out.write(Long.toHexString(size).getBytes("ISO8859_1"));
            out.write(CRLF);
        }

        /**
         * Writes the trailing chunk which ends the stream.
         *
         * @param headers the (optional) trailing headers to write, or null
         * @throws IOException if an error occurs
         */
        public void writeTrailingChunk(Headers headers) throws IOException {
            initChunk(0); // zero-sized chunk marks the end of the stream
            if (headers == null)
                out.write(CRLF); // empty header block
            else
                headers.writeTo(out);
            state = -1;
        }

        /**
         * Writes a chunk containing the given bytes. This method initializes a
         * new chunk with the given size, and then writes the chunk data.
         *
         * @param b an array containing the bytes to write
         * @param off the offset within the array where the data starts
         * @param len the length of the data in bytes
         * @throws IOException if an error occurs
         * @throws IndexOutOfBoundsException if the given offset or length
         *         are outside the bounds of the given array
         */
        public void writeChunk(byte[] b, int off, int len) throws IOException {
            if (len > 0)
                initChunk(len);
            write(b, off, len);
        }
    }

    /**
     * The {@code MultipartInputStream} decodes an InputStream whose data has a
     * "multipart/*" content type (see RFC 1521), providing the underlying data
     * to its various parts.
     */
    public static class MultipartInputStream extends FilterInputStream {

        protected final byte[] boundary;
        protected final int boundaryLength;
        protected final byte[] buf = new byte[4096];
        protected int head, tail;
        protected int extra;

        /**
         * Constructs a MultipartInputStream with the given underlying stream.
         *
         * @param in the underlying multipart stream
         * @param boundary the multipart boundary
         * @throws NullPointerException if the given stream or boundary is null
         * @throws IllegalArgumentException if the given boundary's size is not
         *         between 1 and 70
         */
        protected MultipartInputStream(InputStream in, byte[] boundary) {
            super(in);
            int len = boundary.length;
            if (len < 1 || len > 70)
                throw new IllegalArgumentException("invalid boundary length");
            this.boundary = new byte[len + 2];
            this.boundary[0] = this.boundary[1] = '-';
            System.arraycopy(boundary, 0, this.boundary, 2, len);
            // calculate max boundary length: CRLF--boundary--CRLF
            boundaryLength = len + 8;
        }

        @Override
        public int read() throws IOException {
            if (!fill())
                return -1;
            return buf[head++];
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!fill())
                return -1;
            len = Math.min(tail - head, len);
            System.arraycopy(buf, head, b, off, len);
            head += len;
            return len;
        }

        @Override
        public long skip(long n) throws IOException {
            if (!fill())
                return 0;
            n = Math.min(tail - head, n);
            head += n;
            return n;
        }

        @Override
        public int available() throws IOException {
            return tail - head;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        /**
         * Advances the stream position to the beginning of the next part.
         *
         * @return true if successful, or false if there are no more parts
         * @throws IOException if an error occurs
         */
        public boolean nextPart() throws IOException {
            while (skip(buf.length) != 0); // skip rest of previous part and read boundary
            head = tail = findBoundary()[1]; // start next part after previous boundary
            extra -= tail;
            return fill();
        }

        /**
         * Fills the buffer with more data of the current part.
         *
         * @return true if more data is available, or false if the part's
         *         end has been reached
         * @throws IOException if an error occurs
         */
        protected boolean fill() throws IOException {
            // check if we already have readable data
            if (head != tail)
                return true;
            // shift extra unread data to beginning of buffer
            if (tail > 0 && extra > 0)
                System.arraycopy(buf, tail, buf, 0, extra);
            head = tail = 0;
            // read more until we have at least enough data for a boundary,
            // and if possible, more readable data
            do {
                int read = super.read(buf, extra, buf.length - extra);
                if (read > -1) {
                    extra += read;
                } else if (extra < boundaryLength) {
                    if (extra == 0)
                        return false; // end of stream
                    throw new IOException("missing end boundary");
                }
            } while (extra < boundaryLength);
            // check if there's a boundary and update indices
            int max = findBoundary()[0];
            head = 0;
            tail = max == -1 ? extra - boundaryLength : max;
            extra -= tail;
            return max != 0; // no more readable data in current part
        }

        /**
         * Finds the boundary within the current buffer's valid data.
         *
         * @return an array containing the start index and length of
         *         the found boundary, or -1 if it is not found
         * @throws IOException if an error occurs
         */
        protected int[] findBoundary() throws IOException {
            for (int i = 0; i <= extra - boundary.length; i++) {
                int j = 0;
                while (j < boundary.length && buf[i + j] == boundary[j])
                    j++;
                // if we found the boundary, add the prefix and suffix too
                if (j == boundary.length) {
                    if (buf[i + j] == '-' && buf[i + j + 1] == '-')
                        j += 2; // end of entire multipart
                    if (buf[i + j] != CRLF[0] || buf[i + j + 1] != CRLF[1])
                        throw new IOException("boundary must end with CRLF");
                    // include prefix CRLF, if exists
                    if (i > 1 && buf[i-2] == CRLF[0] && buf[i-1] == CRLF[1]) {
                        i -= 2;
                        j += 2;
                    }
                    return new int[] { i, j + 2 }; // including ending CRLF
                }
            }
            return new int[] { -1, -1 };
        }
    }

    /**
     * The {@code MultipartIterator} iterates over the parts of a multipart/form-data request.
     * <p>
     * For example, to support file upload from a web browser:
     * <ol>
     * <li>Create an HTML form which includes an input field of type "file", attributes
     *     method="post" and enctype="multipart/form-data", and an action URL of your choice,
     *     for example action="/upload". This form can be served normally like any other
     *     resource, e.g. from an HTML file on disk.
     * <li>Add a context handler for the action path ("/upload" in this example), using either
     *     the explicit {@link VirtualHost#addContext} method or the {@link Context} annotation.
     * <li>In the context handler implementation, construct a {@code MultiplartIterator} from
     *     the client {@code Request}.
     * <li>Iterate over the form {@link Part}s, processing each named field as appropriate -
     *     for the file input field, read the uploaded file using the body input stream
     *     (and note that the body input stream must not be closed).
     * </ol>
     */
    public static class MultipartIterator implements Iterator<MultipartIterator.Part> {

        /**
         * The {@code Part} class encapsulates a single part of the multipart.
         * <p>Note: the body input stream must not be closed.
         */
        public static class Part {
            public String name;
            public String filename;
            public Headers headers;
            public InputStream body;

            /***
             * Returns the part's body as a string. If the part
             * headers do not specify a charset, UTF-8 is used.
             *
             * @return the part's body as a string
             * @throws IOException if an IO error occurs
             */
            public String getString() throws IOException {
                String charset = this.headers.getParams("Content-Type").get("charset");
                if (charset == null)
                    charset = "UTF-8";
                return readToken(this.body, -1, charset, 8192);
            }
        }

        protected final MultipartInputStream in;
        protected boolean next;

        /**
         * Creates a new MultipartIterator from the given request.
         *
         * @param req the multipart/form-data request
         * @throws IOException if an IO error occurs
         * @throws IllegalArgumentException if the given request's content type
         *         is not multipart/form-data, or is missing the boundary
         */
        public MultipartIterator(Request req) throws IOException {
            Map<String, String> params = req.getHeaders().getParams("Content-Type");
            if (!params.containsKey("multipart/form-data"))
                throw new IllegalArgumentException("given request is not of type multipart/form-data");
            String boundary = params.get("boundary");
            if (boundary == null)
                throw new IllegalArgumentException("Content-Type is missing boundry");
            in = new MultipartInputStream(req.getBody(), boundary.getBytes("US-ASCII"));
        }

        public boolean hasNext() {
            try {
                return next || (next = in.nextPart());
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        public Part next() {
            if (!hasNext())
                throw new NoSuchElementException();
            next = false;
            Part p = new Part();
            try {
                p.headers = readHeaders(in);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            p.name = p.headers.getParams("Content-Disposition").get("name");
            p.filename = p.headers.getParams("Content-Disposition").get("filename");
            p.body = in;
            return p;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The {@code VirtualHost} class represents a virtual host in the server.
     */
    public static class VirtualHost {

        public static final String DEFAULT_HOST_NAME = "~DEFAULT~";

        protected final String name;
        protected final Set<String> aliases = new CopyOnWriteArraySet<String>();
        protected final Map<String, ContextHandler> contexts =
            new ConcurrentHashMap<String, ContextHandler>();
        protected volatile String directoryIndex = "index.html";
        private volatile boolean allowGeneratedIndex;

        /**
         * Constructs a VirtualHost with the given name.
         *
         * @param name the host's name, or null if it is the default host
         */
        public VirtualHost(String name) {
            this.name = name;
        }

        /**
         * Returns this host's name.
         *
         * @return this host's name, or null if it is the default host
         */
        public String getName() {
            return name;
        }

        /**
         * Adds an alias for this host.
         *
         * @param alias the alias
         */
        public void addAlias(String alias) {
            aliases.add(alias);
        }

        /**
         * Returns this host's aliases.
         *
         * @return the (unmodifiable) set of aliases (which may be empty)
         */
        public Set<String> getAliases() {
            return Collections.unmodifiableSet(aliases);
        }

        /**
         * Sets the directory index file. For every request whose URI ends with
         * a '/' (i.e. a directory), the index file is appended to the path,
         * and the resulting resource is served if it exists. If it does not
         * exist, an auto-generated index for the requested directory may be
         * served, depending on whether {@link #setAllowGeneratedIndex
         * a generated index is allowed}, otherwise an error is returned.
         * The default directory index file is "index.html".
         *
         * @param directoryIndex the directory index file, or null if no
         *        index file should be used
         */
        public void setDirectoryIndex(String directoryIndex) {
            this.directoryIndex = directoryIndex;
        }

        /**
         * Gets this host's directory index file.
         *
         * @return the directory index file, or null
         */
        public String getDirectoryIndex() {
            return directoryIndex;
        }

        /**
         * Sets whether auto-generated indices are allowed. If false,
         * and a directory resource is requested, an error will be
         * returned instead.
         *
         * @param allowed specifies whether generated indices are allowed
         */
        public void setAllowGeneratedIndex(boolean allowed) {
            this.allowGeneratedIndex = allowed;
        }

        /**
         * Returns whether auto-generated indices are allowed.
         *
         * @return whether auto-generated indices are allowed
         */
        public boolean isAllowGeneratedIndex() {
            return allowGeneratedIndex;
        }

        /**
         * Returns the context handler for the given path.
         *
         * If a context is not found for the given path, the search is repeated
         * for its parent path, and so on until a base context is found. If
         * neither the given path nor any of its parents has a context,
         * null is returned.
         *
         * @param path the context's path
         * @return a context handler for the given path, or null if none exists
         */
        public ContextHandler getContext(String path) {
            path = trimRight(path, '/'); // remove trailing slash
            ContextHandler handler = null;
            while (handler == null && path != null) {
                handler = contexts.get(path);
                path = getParentPath(path);
            }
            return handler;
        }

        /**
         * Adds a context and its corresponding context handler to this server.
         * Paths are normalized by removing trailing slashes (except the root).
         *
         * @param path the context's path (must start with '/')
         * @param handler the context handler for the given path
         * @throws IllegalArgumentException if path is malformed
         */
        public void addContext(String path, ContextHandler handler) {
            if (path == null || !path.startsWith("/"))
                throw new IllegalArgumentException("invalid path: " + path);
            contexts.put(trimRight(path, '/'), handler);
        }

        /**
         * Adds context for all methods of the given object that are annotated
         * with the {@link Context} annotation. The methods must have the exact
         * same signature as {@link ContextHandler#serve(Request, Response)}
         * and implement the same contract.
         *
         * @param o the object whose annotated methods are added
         * @throws IllegalArgumentException if a Context-annotated method
         *         is invalid
         */
        public void addContexts(Object o) throws IllegalArgumentException {
            for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                // add to contexts those with @Context annotation
                for (Method m : c.getDeclaredMethods()) {
                    Context context = m.getAnnotation(Context.class);
                    if (context != null) {
                        m.setAccessible(true); // allow access to private member
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length != 2
                            || !Request.class.isAssignableFrom(params[0])
                            || !Response.class.isAssignableFrom(params[1])
                            || !int.class.isAssignableFrom(m.getReturnType()))
                                throw new IllegalArgumentException(
                                    "@Context used with invalid method: " + m);
                        String path = context.value();
                        addContext(path, new MethodContextHandler(m, o));
                    }
                }
            }
        }
    }

    /**
     * The {@code Context} annotation decorates fields which are mapped
     * to a context (path) within the server, and provide its contents.
     *
     * @see VirtualHost#addContexts(Object)
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Context {

        /**
         * The context (path) that this field maps to (must begin with '/').
         */
        String value();
    }

    /**
     * A {@code ContextHandler} is capable of serving content for
     * resources within its context.
     *
     * @see VirtualHost#addContext(String, ContextHandler)
     */
    public static interface ContextHandler {

        /**
         * Serves the given request using the given response.
         *
         * @param req the request to be served
         * @param resp the response to be filled
         * @return an HTTP status code, which will be used in returning
         *         a default response appropriate for this status. If this
         *         method invocation already sent anything in the response
         *         (headers or content), it must return 0, and no further
         *         processing will be done
         * @throws IOException if an IO error occurs
         */
        public int serve(Request req, Response resp) throws IOException;
    }

    /**
     * The {@code FileContextHandler} services a context by mapping it
     * to a file or folder (recursively) on disk.
     */
    public static class FileContextHandler implements ContextHandler {

        protected final File base;
        protected final String context;

        public FileContextHandler(File dir, String context) throws IOException {
            this.base = dir.getCanonicalFile();
            this.context = trimRight(context, '/'); // remove trailing slash;
        }

        public int serve(Request req, Response resp) throws IOException {
            return serveFile(base, context, req, resp);
        }
    }

    /**
     * The {@code MethodContextHandler} services a context by invoking
     * a handler method on a specified object.
     *
     * @see VirtualHost#addContexts(Object)
     */
    public static class MethodContextHandler implements ContextHandler {

        protected final Method m;
        protected final Object obj;

        public MethodContextHandler(Method m, Object obj) {
            this.m = m;
            this.obj = obj;
        }

        public int serve(Request req, Response resp) throws IOException {
            try {
                return (Integer)m.invoke(obj, req, resp);
            } catch (InvocationTargetException ite) {
                throw new IOException("error: " + ite.getCause().getMessage());
            } catch (Exception e) {
                throw new IOException("error: " + e);
            }
        }
    }

    /**
     * The {@code Header} class encapsulates a single HTTP header.
     */
    public static class Header {

        protected final String name;
        protected final String value;

        /**
         * Constructs a header with the given name and value.
         * Leading and trailing whitespace are trimmed.
         *
         * @param name the header name
         * @param value the header value
         * @throws NullPointerException if name or value is null
         * @throws IllegalArgumentException if name is empty
         */
        public Header(String name, String value) {
            this.name = name.trim();
            this.value = value.trim();
            // RFC2616#14.23 - header can have an empty value (e.g. Host)
            if (this.name.length() == 0)
                throw new IllegalArgumentException("name cannot be empty");
        }

        /**
         * Returns this header's name.
         *
         * @return this header's name
         */
        public String getName() { return name; }

        /**
         * Returns this header's value.
         *
         * @return this header's value
         */
        public String getValue() { return value; }
    }

    /**
     * The {@code Headers} class encapsulates a collection of HTTP headers.
     *
     * Header names are treated case-insensitively, although this class retains
     * their original case. Header insertion order is maintained as well.
     */
    public static class Headers implements Iterable<Header> {

        // due to the requirements of case-insensitive name comparisons,
        // retaining the original case, and retaining header insertion order,
        // and due to the fact that the number of headers is generally
        // quite small (usually under 12 headers), we use a simple array with
        // linear access times, which proves to be more efficient and
        // straightforward than the alternatives
        protected Header[] headers = new Header[12];
        protected int count;

        /**
         * Returns the number of added headers.
         *
         * @return the number of added headers
         */
        public int size() {
            return count;
        }

        /**
         * Returns the value of the first header with the given name.
         *
         * @param name the header name (case insensitive)
         * @return the header value, or null if none exists
         */
        public String get(String name) {
            for (int i = 0; i < count; i++)
                if (headers[i].getName().equalsIgnoreCase(name))
                    return headers[i].getValue();
            return null;
        }

        /**
         * Returns the Date value of the header with the given name.
         *
         * @param name the header name (case insensitive)
         * @return the header value as a Date, or null if none exists
         *         or if the value is not in any supported date format
         */
        public Date getDate(String name) {
            try {
                String header = get(name);
                return header == null ? null : parseDate(header);
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }

        /**
         * Returns whether there exists a header with the given name.
         *
         * @param name the header name (case insensitive)
         * @return whether there exists a header with the given name
         */
        public boolean contains(String name) {
            return get(name) != null;
        }

        /**
         * Adds a header with the given name and value to the end of this
         * collection of headers. Leading and trailing whitespace are trimmed.
         *
         * @param name the header name (case insensitive)
         * @param value the header value
         */
        public void add(String name, String value) {
            Header header = new Header(name, value); // also validates
            // expand array if necessary
            if (count == headers.length) {
                Header[] spacious = new Header[2 * count];
                System.arraycopy(headers, 0, spacious, 0, count);
                headers = spacious;
            }
            headers[count++] = header; // inlining header would cause a bug!
        }

        /**
         * Adds all given headers to the end of this collection of headers,
         * in their original order.
         *
         * @param headers the headers to add
         */
        public void addAll(Headers headers) {
            for (Header header : headers)
                add(header.getName(), header.getValue());
        }

        /**
         * Adds a header with the given name and value, replacing the first
         * existing header with the same name. If there is no existing header
         * with the same name, it is added as in {@link #add}.
         *
         * @param name the header name (case insensitive)
         * @param value the header value
         * @return the replaced header, or null if none existed
         */
        public Header replace(String name, String value) {
            for (int i = 0; i < count; i++) {
                if (headers[i].getName().equalsIgnoreCase(name)) {
                    Header prev = headers[i];
                    headers[i] = new Header(name, value);
                    return prev;
                }
            }
            add(name, value);
            return null;
        }

        /**
         * Removes all headers with the given name (if any exist).
         *
         * @param name the header name (case insensitive)
         */
        public void remove(String name) {
            int j = 0;
            for (int i = 0; i < count; i++)
                if (!headers[i].getName().equalsIgnoreCase(name))
                    headers[j++] = headers[i];
            while (count > j)
                headers[--count] = null;
        }

        /**
         * Writes the headers to the given stream (including trailing CRLF).
         *
         * @param out the stream to write the headers to
         * @throws IOException if an error occurs
         */
        public void writeTo(OutputStream out) throws IOException {
            for (int i = 0; i < count; i++) {
                String s = headers[i].getName() + ": " + headers[i].getValue();
                out.write(s.getBytes("ISO8859_1"));
                out.write(CRLF);
            }
            out.write(CRLF); // ends header block
        }

        /**
         * Returns a header's parameters. Parameter order is maintained,
         * and the first key (in iteration order) is the header's value
         * without the parameters.
         *
         * @param name the header name (case insensitive)
         * @return the header's parameter names and values
         */
        public Map<String, String> getParams(String name) {
            Map<String, String> params = new LinkedHashMap<String, String>();
            for (String param : split(get(name), ';')) {
                String[] pair = split(param, '=');
                String val = pair.length == 1 ? "" : trimLeft(trimRight(pair[1], '"'), '"');
                params.put(pair[0], val);
            }
            return params;
        }

        /**
         * Returns an iterator over the headers, in their insertion order.
         * If the headers collection is modified during iteration, the
         * iteration result is undefined. The remove operation is unsupported.
         *
         * @return an Iterator over the headers
         */
        public Iterator<Header> iterator() {
            return new Iterator<Header>() {

                int ind;

                public boolean hasNext() {
                    return ind < count;
                }

                public Header next() {
                    if (ind == count)
                        throw new NoSuchElementException();
                    return headers[ind++];
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    /**
     * The {@code Request} class encapsulates a single HTTP request.
     */
    public class Request {

        protected String method;
        protected URI uri;
        protected String version;
        protected Headers headers;
        protected InputStream body;
        protected Map<String, String> params; // cached value

        /**
         * Constructs a Request from the data in the given input stream.
         *
         * @param in the input stream from which the request is read
         * @throws IOException if an error occurs
         */
        public Request(InputStream in) throws IOException {
            readRequestLine(in);
            headers = readHeaders(in);
            // RFC2616#3.6 - if "chunked" is used, it must be the last one
            // RFC2616#4.4 - if non-identity Transfer-Encoding is present,
            // it must either include "chunked" or close the connection after
            // the body, and in any case ignore Content-Length.
            // if there is no such Transfer-Encoding, use Content-Length
            // if neither header exists, there is no body
            String header = headers.get("Transfer-Encoding");
            if (header != null && !header.equals("identity")) {
                if (header.toLowerCase().contains("chunked"))
                    body = new ChunkedInputStream(in, headers);
                else
                    body = in; // body ends when connection closes
            } else {
                header = headers.get("Content-Length");
                long len = header == null ? 0 : parseULong(header, 10);
                body = new LimitedInputStream(in, len, false);
            }
        }

        /**
         * Returns the request method.
         *
         * @return the request method
         */
        public String getMethod() { return method; }

        /**
         * Returns the request URI.
         *
         * @return the request URI
         */
        public URI getURI() { return uri; }

        /**
         * Returns the request version string.
         *
         * @return the request version string
         */
        public String getVersion() { return version; }

        /**
         * Returns the input stream containing the request body.
         *
         * @return the input stream containing the request body
         */
        public InputStream getBody() { return body; }

        /**
         * Returns the request headers collection.
         *
         * @return the request headers collection
         */
        public Headers getHeaders() { return headers; }

        /**
         * Returns the path component of the request URI,
         * after URL decoding has been applied (using the UTF-8 charset).
         *
         * @return the decoded path component of the request URI
         */
        public String getPath() {
            return uri.getPath();
        }

        /**
         * Sets the path component of the request URI. This can be useful
         * in URL rewriting, etc.
         *
         * @param path the path to set
         * @throws IllegalArgumentException if the given path is malformed
         */
        public void setPath(String path) {
            try {
                uri = new URI(uri.getScheme(), uri.getHost(),
                    trimDuplicates(path, '/'), uri.getFragment());
            } catch (URISyntaxException use) {
                throw new IllegalArgumentException("error setting path", use);
            }
        }

        /**
         * Returns the base URL (scheme, host and port) of the request resource.
         * The host name is taken from the request URI or the Host header or a
         * default host (see RFC2616#5.2).
         *
         * @return the base URL of the requested resource, or null if it
         *         is malformed
         */
        public URL getBaseURL() {
            // normalize host header
            String host = uri.getHost();
            if (host == null) {
                host = headers.get("Host");
                if (host == null) // missing in HTTP/1.0
                    host = detectLocalHostName();
            }
            int pos = host.indexOf(':');
            host = pos == -1 ? host : host.substring(0, pos);
            try {
                return new URL("http", host, port, "");
            } catch (MalformedURLException mue) {
                return null;
            }
        }

        /**
         * Consumes (reads and discards) the entire request body.
         *
         * @throws IOException if an error occurs
         */
        public void consumeBody() throws IOException {
            if (body.read() != -1) { // small optimization
                byte[] b = new byte[4096];
                while (body.read(b) != -1);
            }
        }

        /**
         * Returns the request parameters, which are parsed both from the query
         * part of the request URI, and from the request body if its content
         * type is "application/x-www-form-urlencoded" (i.e. submitted form).
         * UTF-8 encoding is assumed in both cases.
         *
         * @return the request parameters name-value pairs
         * @throws IOException if an error occurs
         * @see HTTPServer#parseParams(String)
         */
        public Map<String, String> getParams() throws IOException {
            if (params != null)
                return params;
            params = new LinkedHashMap<String, String>();
            String paramString = uri.getRawQuery();
            if (paramString != null)
                params.putAll(parseParams(paramString));
            String contentType = headers.get("Content-Type");
            if (contentType != null && contentType.toLowerCase()
                    .startsWith("application/x-www-form-urlencoded"))
                params.putAll(parseParams(
                    readToken(body, -1, "UTF-8", 2097152))); // 2MB limit
            return params;
        }

        /**
         * Returns the absolute (zero-based) content range value read
         * from the Range header. If multiple ranges are requested, a single
         * range containing all of them is returned.
         *
         * @param length the full length of the requested resource
         * @return the requested range, or null if the Range header
         *         is missing or invalid
         */
        public long[] getRange(long length) {
            String header = headers.get("Range");
            return header == null || !header.startsWith("bytes=")
                ? null : parseRange(header.substring(6), length);
        }

        /**
         * Reads the request line, parsing the method, URI and version string.
         *
         * @param in the input stream from which the request line is read
         * @throws IOException if an error occurs or the request line is invalid
         */
        protected void readRequestLine(InputStream in) throws IOException {
            // RFC2616#4.1: should accept empty lines before request line
            // RFC2616#19.3: tolerate additional whitespace between tokens
            String line;
            do { line = readLine(in); } while (line.length() == 0);
            String[] tokens = split(line, ' ');
            if (tokens.length != 3)
                throw new IOException("invalid request line: \"" + line + "\"");
            try {
                method = tokens[0];
                // must remove '//' prefix which constructor parses as host name
                uri = new URI(trimDuplicates(tokens[1], '/'));
                version = tokens[2];
            } catch (URISyntaxException use) {
                throw new IOException("invalid URI: " + use.getMessage());
            }
        }

        /**
         * Returns the virtual host corresponding to the requested host name,
         * or the default host if none exists.
         *
         * @return the virtual host corresponding to the requested host name,
         *         or the default virtual host
         */
        public VirtualHost getVirtualHost() {
            String name = getBaseURL().getHost();
            VirtualHost host = HTTPServer.this.getVirtualHost(name);
            return host != null ? host : HTTPServer.this.getVirtualHost(null);
        }
    }

    /**
     * The {@code Response} class encapsulates a single HTTP response.
     */
    public class Response {

        protected OutputStream out;
        protected Headers headers;
        protected boolean discardBody;

        /**
         * Constructs a Response whose output is written to the given stream.
         *
         * @param out the stream to which the response is written
         */
        public Response(OutputStream out) {
            this.out = out;
            this.headers = new Headers();
        }

        /**
         * Sets whether this response's body is discarded or sent.
         *
         * @param discardBody specifies whether the body is discarded or not
         */
        public void setDiscardBody(boolean discardBody) {
            this.discardBody = discardBody;
        }

        /**
         * Returns whether the response body is discarded.
         *
         * @return true if the response body is discarded, false otherwise
         */
        public boolean isDiscardBody() {
            return discardBody;
        }

        /**
         * Returns an output stream into which the response body can be written.
         * The body must be written only after the headers have been sent.
         * If the body length is unknown, use {@link #getChunkedBody()} instead.
         *
         * @return an output stream into which the response body can be written,
         *         or null if isDiscardBody() returns true, in which case the
         *         body should not be written
         */
        public OutputStream getBody() {
            return discardBody ? null : out;
        }

        /**
         * Adds a chunked Transfer-Encoding header to the response, and returns
         * a chunked output stream into which the response body can be written.
         * This should be used instead of {@link #getBody} only when the body
         * length is not known in advance.
         * The body must be written only after the headers have been sent (but
         * this method modifies the headers, so it must be called first).
         *
         * @return a chunked output stream into which the response body can be
         *         written, or null if isDiscardBody() returns true, in which
         *         case the body should not be written
         */
        public ChunkedOutputStream getChunkedBody() {
            headers.replace("Transfer-Encoding", "chunked");
            return discardBody ? null : new ChunkedOutputStream(out);
        }

        /**
         * Returns the request headers collection.
         *
         * @return the request headers collection
         */
        public Headers getHeaders() { return headers; }

        /**
         * Sends the response headers with the given response status.
         * A Date header is added if it does not already exist.
         *
         * @param status the response status
         * @throws IOException if an error occurs
         */
        public void sendHeaders(int status) throws IOException {
            if (!headers.contains("Date"))
                headers.add("Date", formatDate(System.currentTimeMillis()));
            headers.add("Server", "freeutils-HTTPServer/1.0");
            String line = "HTTP/1.1 " + status + " " + statuses[status];
            out.write(line.getBytes("ISO8859_1"));
            out.write(CRLF);
            headers.writeTo(out);
            out.flush();
        }

        /**
         * Sends the response headers, including the given response status
         * and description, and all response headers. If they do not already
         * exist, the following headers are added as necessary:
         * Content-Range, Content-Length, Content-Type, Last-Modified,
         * ETag and Date. Ranges are properly calculated as well, with a 200
         * status changed to a 206 status.
         *
         * @param status the response status
         * @param length the response body length, or negative if unknown
         * @param lastModified the last modified date of the response resource,
         *        or non-positive if unknown. A time in the future will be
         *        replaced with the current system time.
         * @param etag the ETag of the response resource, or null if unknown
         *        (see RFC2616#3.11)
         * @param contentType the content type of the response resource, or
         *        null if unknown (in which case "application/octet-stream"
         *        will be sent)
         * @param range the content range that will be sent, or null if the
         *        entire resource will be sent
         * @throws IOException if an error occurs
         */
        public void sendHeaders(int status, long length,
                long lastModified, String etag, String contentType,
                long[] range) throws IOException {
            if (range != null) {
                headers.add("Content-Range", "bytes " + range[0] + "-" +
                    range[1] + "/" + (length >= 0 ? length : "*"));
                length = range[1] - range[0] + 1;
                if (status == 200)
                    status = 206;
            }
            if (length >= 0 && !headers.contains("Content-Length")
                            && !headers.contains("Transfer-Encoding"))
                headers.add("Content-Length", Long.toString(length));
            if (!headers.contains("Content-Type")) {
                if (contentType == null)
                    contentType = "application/octet-stream";
                headers.add("Content-Type", contentType);
            }
            if (lastModified > 0 && !headers.contains("Last-Modified")) {
                if (lastModified > System.currentTimeMillis()) // RFC2616#14.29
                    lastModified = System.currentTimeMillis();
                headers.add("Last-Modified", formatDate(lastModified));
            }
            if (etag != null && !headers.contains("ETag"))
                headers.add("ETag", etag);
            sendHeaders(status);
        }

        /**
         * Sends the full response with the given status, and the given string
         * as the body. The text is sent in the UTF-8 charset. If a
         * Content-Type header was not explicitly set, it will be set to
         * text/html, and so the text must contain valid (and properly
         * {@link HTTPServer#escapeHTML escaped}) HTML.
         *
         * @param status the response status
         * @param text the text body (sent as text/html)
         * @throws IOException if an error occurs
         */
        public void send(int status, String text) throws IOException {
            byte[] content = text.getBytes("UTF-8");
            sendHeaders(status, content.length, -1,
                "\"H" + Integer.toHexString(text.hashCode()) + "\"",
                "text/html; charset=utf-8", null);
            if (!discardBody)
                out.write(content);
            out.flush();
        }

        /**
         * Sends an error response with the given status and detailed message.
         * An HTML body is created containing the status and its description,
         * as well as the message, which is escaped using the
         * {@link HTTPServer#escapeHTML escape} method.
         *
         * @param status the response status
         * @param text the text body (sent as text/html)
         * @throws IOException if an error occurs
         */
        public void sendError(int status, String text) throws IOException {
            @SuppressWarnings("resource")
            Formatter f = new Formatter();
            f.format("<!DOCTYPE html>%n" +
                "<html>%n<head><title>%d %s</title></head>%n" +
                "<body><h1>%d %s</h1>%n<p>%s</p>%n</body></html>",
                status, statuses[status], status, statuses[status],
                escapeHTML(text));
            send(status, f.toString());
        }

        /**
         * Sends an error response with the given status and default body.
         *
         * @param status the response status
         * @throws IOException if an error occurs
         */
        public void sendError(int status) throws IOException {
            String text = status < 400 ? ":)" : "sorry it didn't work out :(";
            sendError(status, text);
        }

        /**
         * Sends the request body. This method must be called only after the
         * response headers have been sent (and indicate that there is a body).
         *
         * @param body a stream containing the response body
         * @param length the full length of the response body
         * @param range the subrange within the request body that should be
         *        sent, or null if the entire body should be sent
         * @throws IOException if an error occurs
         */
        public void sendBody(InputStream body, long length, long[] range)
                throws IOException {
            if (!discardBody) {
                if (range != null) {
                    long offset = range[0];
                    length = range[1] - range[0] + 1;
                    while (offset > 0) {
                        long skipped = body.skip(offset);
                        if (skipped == 0)
                            throw new IOException("can't skip to " + range[0]);
                        offset -= skipped;
                    }
                }
                transfer(body, out, length);
            }
            out.flush();
        }

        /**
         * Sends a 301 or 302 response, redirecting the client to the given URL.
         *
         * @param url the absolute URL to which the client is redirected
         * @param permanent specifies whether a permanent (301) or
         *        temporary (302) redirect status is sent
         * @throws IOException if an IO error occurs or url is malformed
         */
        public void redirect(String url, boolean permanent) throws IOException {
            try {
                url = new URI(url).toASCIIString();
            } catch (URISyntaxException e) {
                throw new IOException("malformed URL: " + url);
            }
            headers.add("Location", url);
            // some user-agents expect a body, so we send it
            if (permanent)
                sendError(301, "Permanently moved to " + url);
            else
                sendError(302, "Temporarily moved to " + url);
        }
    }

    /**
     * The {@code SocketHandlerThread} handles accepted sockets.
     */
    protected class SocketHandlerThread extends Thread {

        public SocketHandlerThread() {
            setName(getClass().getSimpleName() + "-" + port);
        }

        @SuppressWarnings("resource")
      @Override
        public void run() {
            try {
                while (!serv.isClosed()) {
                    final Socket sock = serv.accept();
                    executor.execute(new Runnable() {
                        public void run() {
                            try {
                                sock.setSoTimeout(10000);
                                handleConnection(sock);
                            } catch (IOException ignore) {
                            } finally {
                                try {
                                    sock.close();
                                } catch (IOException ignore) {}
                            }
                        }
                    });
                }
            } catch (IOException ignore) {}
        }
    }

    protected volatile int port;
    protected volatile Executor executor;
    protected volatile ServerSocket serv;
    protected final Map<String, VirtualHost> hosts =
        new ConcurrentHashMap<String, VirtualHost>();

    /**
     * Constructs an HTTPServer which can accept connections on the given port.
     * Note: the {@link #start()} method must be called to start accepting
     * connections.
     *
     * @param port the port on which this server will accept connections
     */
    public HTTPServer(int port) {
        setPort(port);
        addVirtualHost(new VirtualHost(null)); // add default virtual host
    }

    /**
     * Constructs an HTTPServer which can accept connections on the default HTTP port 80.
     * Note: the {@link #start()} method must be called to start accepting
     * connections.
     */
    public HTTPServer() {
        this(80);
    }

    /**
     * Sets the port on which this server will accept connections.
     *
     * @param port the port on which this server will accept connections
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the Executor used in servicing HTTP connections. If null,
     * a default Executor is used.
     *
     * @param executor the executor to use
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * Returns the virtual host with the given name.
     *
     * @param name the name of the virtual host to return, or null for
     *        the default virtual host
     * @return the virtual host with the given name, or null if it doesn't exist
     */
    public VirtualHost getVirtualHost(String name) {
        return hosts.get(name == null ? VirtualHost.DEFAULT_HOST_NAME : name);
    }

    /**
     * Returns all virtual hosts.
     *
     * @return all virtual hosts (as an unmodifiable set)
     */
    public Set<VirtualHost> getVirtualHosts() {
        return Collections.unmodifiableSet(
            new HashSet<VirtualHost>(hosts.values()));
    }

    /**
     * Adds the given virtual host to the server.
     * If the host's name or aliases already exist, they are overwritten.
     *
     * @param host the virtual host to add
     */
    public void addVirtualHost(VirtualHost host) {
        String name = host.getName();
        hosts.put(name == null ? VirtualHost.DEFAULT_HOST_NAME : name, host);
    }

    /**
     * Starts this server. If it is already started, does nothing.
     * Note: Once the server is started, configuration-altering methods
     * of the server and its virtual hosts must not be used. To modify the
     * configuration, the server must first be stopped.
     *
     * @throws IOException if the server cannot begin accepting connections
     */
    public synchronized void start() throws IOException {
        if (serv != null)
            return;
        serv = new ServerSocket(port);
        if (executor == null) // assign default executor if needed
            executor = Executors.newCachedThreadPool();
        // register all host aliases (which may have been modified)
        for (VirtualHost host : getVirtualHosts())
            for (String alias : host.getAliases())
                hosts.put(alias, host);
        // start handling incoming connections
        new SocketHandlerThread().start();
    }

    /**
     * Stops this server. If it is already stopped, does nothing.
     */
    public synchronized void stop() {
        try {
            if (serv != null)
                serv.close();
        } catch (IOException ignore) {}
        serv = null;
    }

    /**
     * Handles communications for a single connection, on the given socket.
     * Multiple subsequent transactions are handled on the connection, until
     * the socket is closed by either side, an error occurs, or the request
     * contains a "Connection: close" header which explicitly requests the
     * connection be closed after the transaction ends.
     *
     * @param sock the socket on which communications are handled
     * @throws IOException if and error occurs
     */
    protected void handleConnection(Socket sock) throws IOException {
        OutputStream out = new BufferedOutputStream(
            sock.getOutputStream(), 4096);
        InputStream in = new BufferedInputStream(sock.getInputStream(), 4096);
        String connectionHeader;
        do {
            // create request and response
            Response resp = new Response(out);
            Request req;
            try {
                req = new Request(in);
            } catch (InterruptedIOException ignore) { // timeout
                break;
            } catch (IOException ioe) {
                resp.sendError(400, "invalid request: " + ioe.getMessage());
                break;
            }
            // handle request
            try {
                handleTransaction(req, resp);
            } catch (InterruptedIOException ignore) { // timeout
                break;
            } catch (IOException ioe) {
                resp.sendError(500,
                    "error processing request: " + ioe.getMessage());
                break;
            }
            out.flush(); // flush response output
            // consume any leftover body data so next request can be processed
            req.consumeBody();
            // persist or close connection as necessary
            connectionHeader = req.getHeaders().get("Connection");
        } while (!"close".equalsIgnoreCase(connectionHeader));
    }

    /**
     * Handles a single transaction on a connection.
     *
     * Subclasses can override this method to perform filtering on the
     * request or response, apply wrappers to them, or further customize
     * the transaction processing in some other way.
     *
     * @param req the transaction request
     * @param resp the transaction response (into which the response is written)
     * @throws IOException if and error occurs
     */
    protected void handleTransaction(Request req, Response resp) throws IOException {
        if (preprocessTransaction(req, resp))
            handleMethod(req, resp);
    }

    /**
     * Preprocesses a transaction, performing various validation checks
     * and required special header handling, possibly returning an
     * appropriate response.
     *
     * @param req the request
     * @param resp the response
     * @return whether further processing should be performed on the transaction
     * @throws IOException if an error occurs
     */
    protected boolean preprocessTransaction(Request req, Response resp) throws IOException {
        Headers reqHeaders = req.getHeaders();
        // validate request
        String version = req.getVersion();
        if (version.equals("HTTP/1.1")) {
            if (!reqHeaders.contains("Host")) {
                // RFC2616#14.23: missing Host header gets 400
                resp.sendError(400, "missing required Host header");
                return false;
            }
            // return a continue response before reading body
            String expect = reqHeaders.get("Expect");
            if (expect != null) {
                if (expect.equalsIgnoreCase("100-continue")) {
                    Response tempResp = new Response(resp.getBody());
                    tempResp.sendHeaders(100);
                } else {
                    // RFC2616#14.20: if unknown expect, send 417
                    resp.sendError(417);
                    return false;
                }
            }
        } else if (version.equals("HTTP/1.0") || version.equals("HTTP/0.9")) {
            // RFC2616#14.10 - remove connection headers from older versions
            for (String token : splitElements(reqHeaders.get("Connection")))
                reqHeaders.remove(token);
        } else {
            resp.sendError(400, "unknown version: " + version);
            return false;
        }
        return true;
    }

    /**
     * Handles a transaction according to the request method.
     *
     * @param req the transaction request
     * @param resp the transaction response (into which the response is written)
     * @throws IOException if and error occurs
     */
    protected void handleMethod(Request req, Response resp) throws IOException {
        String method = req.getMethod();
        if (method.equals("GET") || method.equals("POST")) {
            serve(req, resp);
        } else if (method.equals("HEAD")) { // process normally but discard body
            resp.setDiscardBody(true);
            serve(req, resp);
        } else if (method.equals("OPTIONS")) {
            resp.getHeaders().add("Allow", "GET, HEAD, POST, OPTIONS, TRACE");
            resp.getHeaders().add("Content-Length", "0"); // RFC2616#9.2
            resp.sendHeaders(200);
        } else if (method.equals("TRACE")) {
            handleTrace(req, resp);
        } else {
            resp.sendError(501, "unsupported method: " + method);
        }
    }

    /**
     * Handles a TRACE method request.
     *
     * @param req the request
     * @param resp the response into which the content is written
     * @throws IOException if an error occurs
     */
    @SuppressWarnings("resource")
   public void handleTrace(Request req, Response resp) throws IOException {
        ChunkedOutputStream out = resp.getChunkedBody();
        resp.getHeaders().add("Content-Type", "message/http");
        resp.sendHeaders(200);
        ByteArrayOutputStream headout = new ByteArrayOutputStream();
        String responseLine = "TRACE " + req.getURI() + " " + req.getVersion();
        headout.write(responseLine.getBytes("ISO8859_1"));
        headout.write(CRLF);
        req.getHeaders().writeTo(headout);
        byte[] b = headout.toByteArray();
        out.writeChunk(b, 0, b.length);
        b = new byte[4096];
        int count;
        InputStream in = req.getBody();
        while ((count = in.read(b)) != -1)
            out.writeChunk(b, 0, count);
        out.writeTrailingChunk(null);
    }

    /**
     * Serves the content for a request, using the context handler for the
     * requested context.
     *
     * @param req the request
     * @param resp the response into which the content is written
     * @throws IOException if an error occurs
     */
    protected void serve(Request req, Response resp) throws IOException {
        // get context handler to handle request
        String path = req.getPath();
        ContextHandler handler = req.getVirtualHost().getContext(path);
        if (handler == null) {
            resp.sendError(404);
            return;
        }
        // serve request
        int status = 404;
        // add directory index if necessary
        if (path.endsWith("/")) {
            String index = req.getVirtualHost().getDirectoryIndex();
            if (index != null) {
                req.setPath(path + index);
                status = handler.serve(req, resp);
                req.setPath(path);
            }
        }
        if (status == 404)
            status = handler.serve(req, resp);
        if (status > 0)
            resp.sendError(status);
    }

    /**
     * Adds a Content-Type mapping for the given path suffixes.
     * If any of the path suffixes had a previous Content-Type associated
     * with it, it is replaced with the given one. Path suffixes are
     * considered case-insensitive, and contentType is converted to lowercase.
     *
     * @param contentType the content type (MIME type) to be associated with
     *        the given path suffixes
     * @param suffixes the path suffixes which will be associated with
     *        the contentType, e.g. the file extensions of served files
     *        (excluding the '.' character)
     */
    public static void addContentType(String contentType, String... suffixes) {
        for (String suffix : suffixes)
            contentTypes.put(suffix.toLowerCase(), contentType.toLowerCase());
    }

    /**
     * Returns the content type for the given path, according to its suffix,
     * or the given default content type if none can be determined.
     *
     * @param path the path whose content type is requested
     * @param def a default content type which is returned if none can be
     *        determined
     * @return the content type for the given path, or the given default
     */
    public static String getContentType(String path, String def) {
        int dot = path.lastIndexOf('.');
        String type = dot < 0 ? def
            : contentTypes.get(path.substring(dot + 1).toLowerCase());
        return type != null ? type : def;
    }

    /**
     * Returns the local host's auto-detected name.
     *
     * @return the local host name
     */
    public static String detectLocalHostName() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException uhe) {
            return "localhost";
        }
    }

    /**
     * Parses name-value pair parameters from the given "x-www-form-urlencoded"
     * MIME-type string. This is the encoding used both for parameters passed
     * as the query of an HTTP GET method, and as the content of HTML forms
     * submitted using the HTTP POST method (as long as they use the default
     * "application/x-www-form-urlencoded" encoding in their ENCTYPE attribute).
     * UTF-8 encoding is assumed.
     *
     * @param s an "application/x-www-form-urlencoded" string
     * @return the parameter name-value pairs parsed from the given string,
     *         or an empty map if it does not contain any
     */
    @SuppressWarnings("resource")
   public static Map<String, String> parseParams(String s) {
        if (s == null || s.length() == 0)
            return Collections.emptyMap();
        Map<String, String> params = new LinkedHashMap<String, String>(8);
        Scanner sc = new Scanner(s).useDelimiter("&");
        while (sc.hasNext()) {
            String pair = sc.next();
            int pos = pair.indexOf('=');
            String name = pos == -1 ? pair : pair.substring(0, pos);
            String val = pos == -1 ? "" : pair.substring(pos + 1);
            try {
                name = URLDecoder.decode(name.trim(), "UTF-8");
                val = URLDecoder.decode(val.trim(), "UTF-8");
                if (name.length() > 0)
                    params.put(name, val);
            } catch (UnsupportedEncodingException ignore) {} // never thrown
        }
        return params;
    }

    /**
     * Returns the absolute (zero-based) content range value specified
     * by the given range string. If multiple ranges are requested, a single
     * range containing all of them is returned.
     *
     * @param range the string containing the range description
     * @param length the full length of the requested resource
     * @return the requested range, or null if the range value is invalid
     */
    public static long[] parseRange(String range, long length) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        try {
            for (String token : splitElements(range)) {
                long start, end;
                int dash = token.indexOf('-');
                if (dash == 0) { // suffix range
                    start = length - parseULong(token.substring(1), 10);
                    end = length - 1;
                } else if (dash == token.length() - 1) { // open range
                    start = parseULong(token.substring(0, dash), 10);
                    end = length - 1;
                } else { // explicit range
                    start = parseULong(token.substring(0, dash), 10);
                    end = parseULong(token.substring(dash + 1), 10);
                }
                if (end < start)
                    throw new RuntimeException();
                if (start < min)
                    min = start;
                if (end > max)
                    max = end;
            }
            if (max < 0) // no tokens
                throw new RuntimeException();
            if (max >= length && min < length)
                max = length - 1;
            return new long[] { min, max }; // start might be >= length!
        } catch (RuntimeException re) { // NFE, IOOBE or explicit RE
            return null; // RFC2616#14.35.1 - ignore header if invalid
        }
    }

    /**
     * Parses an unsigned long value. This method behaves the same as calling
     * {@link Long#parseLong(String, int)}, but considers the string invalid
     * if it starts with an ASCII minus sign ('-') or plus sign ('+').
     *
     * @param s the String containing the long representation to be parsed
     * @param radix the radix to be used while parsing s
     * @return the long represented by s in the specified radix
     * @throws NumberFormatException if the string does not contain a parsable
     *         long, or if it starts with an ASCII minus sign or plus sign
     */
    public static long parseULong(String s, int radix)
            throws NumberFormatException {
        long val = Long.parseLong(s, radix); // throws NumberFormatException
        if (s.charAt(0) == '-' || s.charAt(0) == '+')
            throw new NumberFormatException("invalid digit: " + s.charAt(0));
        return val;
    }

    /**
     * Parses a date string in one of the supported {@link #DATE_PATTERNS}.
     *
     * Received date header values must be in one of the following formats:
     * Sun, 06 Nov 1994 08:49:37 GMT  ; RFC 822, updated by RFC 1123
     * Sunday, 06-Nov-94 08:49:37 GMT ; RFC 850, obsoleted by RFC 1036
     * Sun Nov  6 08:49:37 1994       ; ANSI C's asctime() format
     *
     * @param time a string representation of a time value
     * @return the parsed date value
     * @throws IllegalArgumentException if the given string does not contain
     *         a valid date format in any of the supported formats
     */
    public static Date parseDate(String time) {
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(pattern, Locale.US);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                return df.parse(time);
            } catch (ParseException ignore) {}
        }
        throw new IllegalArgumentException("invalid date format: " + time);
    }

    /**
     * Formats the given time value as a string in RFC 1123 format.
     *
     * @param time the time in milliseconds since January 1, 1970, 00:00:00 GMT
     * @return the given time value as a string in RFC 1123 format
     */
    public static String formatDate(long time) {
        return String.format("%ta, %<td %<tb %<tY %<tT GMT", time);
    }

    /**
     * Splits the given element list string (comma-separated header value)
     * into its constituent non-empty trimmed elements.
     * (RFC2616#2.1: element lists are delimited by a comma and optional LWS,
     * and empty elements are ignored).
     *
     * @param list the element list string
     * @return the non-empty elements in the list, or an empty array
     */
    public static String[] splitElements(String list) {
        return split(list, ',');
    }

    /**
     * Splits the given string into its constituent non-empty trimmed elements,
     * which are delimited by the given character. This is a more direct
     * and efficient implementation than using a regex (e.g. String.split()).
     *
     * @param str the string to split
     * @param delim the character used as the delimiter between elements
     * @return the non-empty elements in the string, or an empty array
     */
    public static String[] split(String str, char delim) {
        if (str == null)
            return new String[0];
        Collection<String> elements = new ArrayList<String>();
        int len = str.length();
        int start = 0;
        while (start < len) {
            int end = str.indexOf(delim, start);
            if (end == -1)
                end = len; // last token is until end of string
            String element = str.substring(start, end).trim();
            if (element.length() > 0)
                elements.add(element);
            start = end + 1;
        }
        return elements.toArray(new String[elements.size()]);
    }

    /**
     * Returns the parent of the given path.
     *
     * @param path the path whose parent is returned (must start with '/')
     * @return the parent of the given path (excluding trailing slash),
     *         or null if given path is the root path
     */
    public static String getParentPath(String path) {
        path = trimRight(path, '/'); // remove trailing slash
        int slash = path.lastIndexOf('/');
        return slash == -1 ? null : path.substring(0, slash);
    }

    /**
     * Returns the given string with all occurrences of the given character
     * removed from its right side.
     *
     * @param s the string to trim
     * @param c the character to remove
     * @return the trimmed string
     */
    public static String trimRight(String s, char c) {
        int len = s.length() - 1;
        int end;
        for (end = len; end >= 0 && s.charAt(end) == c; end--);
        return end == len ? s : s.substring(0, end + 1);
    }

    /**
     * Returns the given string with all occurrences of the given character
     * removed from its left side.
     *
     * @param s the string to trim
     * @param c the character to remove
     * @return the trimmed string
     */
    public static String trimLeft(String s, char c) {
        int len = s.length();
        int start;
        for (start = 0; start < len && s.charAt(start) == c; start++);
        return start == 0 ? s : s.substring(start);
    }

    /**
     * Trims duplicate consecutive occurrences of the given character within the
     * given string, replacing them with a single instance of the character.
     *
     * @param s the string to trim
     * @param c the character to trim
     * @return the given string with duplicate consecutive occurrences of c
     *         replaced by a single instance of c
     */
    public static String trimDuplicates(String s, char c) {
        int i = -1;
        while ((i = s.indexOf(c, i + 1)) > -1) {
            int end;
            for (end = i + 1; end < s.length() && s.charAt(end) == c; end++);
            if (end > i + 1)
                s = s.substring(0, i + 1) + s.substring(end);
        }
        return s;
    }

    /**
     * Returns a human-friendly string approximating the given data size,
     * e.g. "316", "1.8K", "324M", etc.
     *
     * @param size the size to display
     * @return a human-friendly string approximating the given data size
     */
    public static String toSizeApproxString(long size) {
        final char[] units = { ' ', 'K', 'M', 'G', 'T', 'P', 'E' };
        int u;
        double s;
        for (u = 0, s = size; s >= 1000; u++, s /= 1024);
        return String.format(s < 10 ? "%.1f%c" : "%.0f%c", s, units[u]);
    }

    /**
     * Returns an HTML-escaped version of the given string for safe display
     * within a web page. The characters '&', '>' and '<' must always be
     * escaped, and single and double quotes must be escaped within
     * attribute values; this method escapes them always. This method can
     * be used for generating both HTML and XHTML valid content.
     *
     * @param s the string to escape
     * @return the escaped string
     * @see <a href="http://www.w3.org/International/questions/qa-escapes">
     *      The W3C FAQ</a>
     */
    public static String escapeHTML(String s) {
        int len = s.length();
        StringBuilder es = new StringBuilder(len + 30);
        int start = 0;
        for (int i = 0; i < len; i++) {
            String ref = null;
            switch (s.charAt(i)) {
                case '&': ref = "&amp;"; break;
                case '>': ref = "&gt;"; break;
                case '<': ref = "&lt;"; break;
                case '"': ref = "&quot;"; break;
                case '\'': ref = "&#39;"; break;
            }
            if (ref != null) {
                es.append(s.substring(start, i)).append(ref);
                start = i + 1;
            }
        }
        return start == 0 ? s : es.append(s.substring(start)).toString();
    }

    /**
     * Transfers data from an input stream to an output stream.
     *
     * @param in the input stream to transfer from
     * @param out the output stream to transfer to
     * @param len the number of bytes to transfer. If negative, the entire
     *        contents of the input stream are transfered.
     * @throws IOException if an IO error occurs or the input stream ends
     *         before the requested number of bytes have been read
     */
    public static void transfer(InputStream in, OutputStream out, long len)
            throws IOException {
        byte[] buf = new byte[4096];
        while (len != 0) {
            int count = len < 0 || buf.length < len ? buf.length : (int)len;
            count = in.read(buf, 0, count);
            if (count == -1) {
                if (len > 0)
                    throw new IOException("unexpected end of stream");
                break;
            }
            out.write(buf, 0, count);
            len -= len > 0 ? count : 0;
        }
    }

    /**
     * Reads the token starting at the current stream position and ending at
     * the first occurrence of the given delimiter byte, in the given encoding.
     *
     * @param in the stream from which the token is read
     * @param delim the byte value which marks the end of the token,
     *        or -1 if the token ends at the end of the stream
     * @param enc a character-encoding name
     * @param maxLength the maximum length (in bytes) to read
     * @return the read token, excluding the delimiter
     * @throws UnsupportedEncodingException if the encoding is not supported
     * @throws IOException if an IO error occurs, or the stream end is
     *         reached before the delimiter is found (and it is not -1),
     *         or the maximum length is reached before the token end is reached
     */
    public static String readToken(InputStream in, int delim,
            String enc, int maxLength) throws IOException {
        // note: we avoid using a ByteArrayOutputStream here because it
        // suffers the overhead of synchronization for each byte written
        int buflen = maxLength < 512 ? maxLength : 512; // start with less
        byte[] buf = new byte[buflen];
        int count = 0;
        int c;
        while ((c = in.read()) != -1 && c != delim) {
            if (count == buflen) { // expand buffer
                if (count == maxLength)
                    throw new IOException("token too large (" + count + ")");
                buflen = maxLength < 2 * buflen ? maxLength : 2 * buflen;
                byte[] expanded = new byte[buflen];
                System.arraycopy(buf, 0, expanded, 0, count);
                buf = expanded;
            }
            buf[count++] = (byte)c;
        }
        if (c == -1 && delim != -1)
            throw new IOException("unexpected end of stream");
        return new String(buf, 0, count, enc);
    }

    /**
     * Reads the ISO-8859-1 encoded string starting at the current stream
     * position and ending at the first occurrence of the LF character.
     *
     * @param in the stream from which the line is read
     * @return the read string, excluding the terminating LF character
     *         and (if exists) the CR character immediately preceding it
     * @throws IOException if an IO error occurs, or the stream end is
     *         reached before an LF character is found, or the line is
     *         longer than 8192 bytes
     * @see #readToken(InputStream, int, String, int)
     */
    public static String readLine(InputStream in) throws IOException {
        String s = readToken(in, '\n', "ISO8859_1", 8192);
        return s.length() > 0 && s.charAt(s.length() - 1) == '\r'
            ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Reads headers from the given stream. Headers are read according to the
     * RFC, including folded headers, element lists, and multiple headers
     * (which are concatenated into a single element list header).
     * Leading and trailing whitespace is removed.
     *
     * @param in the stream from which the headers are read
     * @return the read headers (possibly empty, if none exist)
     * @throws IOException if an IO error occurs or the headers are malformed
     *         or there are more than 100 header lines
     */
    public static Headers readHeaders(InputStream in) throws IOException {
        Headers headers = new Headers();
        String line;
        String prevLine = "";
        int count = 0;
        while ((line = readLine(in)).length() > 0) {
            int first;
            for (first = 0; first < line.length() &&
                Character.isWhitespace(line.charAt(first)); first++);
            if (first > 0) // unfold header continuation line
                line = prevLine + ' ' + line.substring(first);
            int separator = line.indexOf(':');
            if (separator == -1)
                throw new IOException("invalid header: \"" + line + "\"");
            String name = line.substring(0, separator);
            String value = line.substring(separator + 1).trim(); // ignore LWS
            Header replaced = headers.replace(name, value);
            // concatenate repeated headers (distinguishing repeat from fold)
            if (replaced != null && first == 0) {
                value = replaced.getValue() + ", " + value;
                line = name + ": " + value;
                headers.replace(name, value);
            }
            prevLine = line;
            if (++count > 100)
                throw new IOException("too many header lines");
        }
        return headers;
    }

    /**
     * Matches the given ETag value against the given ETags. A match is found
     * if the given ETag is not null, and either the ETags contain a "*" value,
     * or one of them is identical to the given ETag. If strong comparison is
     * used, tags beginning with the weak ETag prefix "W/" never match.
     * See RFC2616#3.11, RFC2616#13.3.3.
     *
     * @param strong if true, strong comparison is used, otherwise weak
     *        comparison is used
     * @param etags the ETags to match against
     * @param etag the ETag to match
     * @return true if the ETag is matched, false otherwise
     */
    public static boolean match(boolean strong, String[] etags, String etag) {
        if (etag == null || strong && etag.startsWith("W/"))
            return false;
        for (String e : etags)
            if (e.equals("*") ||
                    (e.equals(etag) && !(strong && (e.startsWith("W/")))))
                return true;
        return false;
    }

    /**
     * Calculates the appropriate response status for the given request and
     * its resource's last-modified time and ETag, based on the conditional
     * headers present in the request.
     *
     * @param req the request
     * @param lastModified the resource's last modified time
     * @param etag the resource's ETag
     * @return the appropriate response status for the request
     */
    public static int getConditionalStatus(Request req,
            long lastModified, String etag) {
        Headers headers = req.getHeaders();
        // If-Match
        String header = headers.get("If-Match");
        if (header != null && !match(true, splitElements(header), etag))
            return 412;
        // If-Unmodified-Since
        Date date = headers.getDate("If-Unmodified-Since");
        if (date != null && lastModified > date.getTime())
            return 412;
        // If-Modified-Since
        int status = 200;
        boolean force = false;
        date = headers.getDate("If-Modified-Since");
        if (date != null && date.getTime() <= System.currentTimeMillis()) {
            if (lastModified > date.getTime())
                force = true;
            else
                status = 304;
        }
        // If-None-Match
        header = headers.get("If-None-Match");
        if (header != null) {
            if (match(true, splitElements(header), etag))
                status = req.getMethod().equals("GET")
                    || req.getMethod().equals("HEAD") ? 304 : 412;
            else
                force = true;
        }
        return force ? 200 : status;
    }

    /**
     * Serves a context's contents from a file based resource.
     *
     * The file is located by stripping the given context prefix from
     * the request's path, and appending the result to the given base directory.
     *
     * Missing, forbidden and otherwise invalid files return the appropriate
     * error response. Directories are served as an HTML index page if the
     * virtual host allows one, or a forbidden error otherwise. Files are
     * sent with their corresponding content types, and handle conditional
     * and partial retrievals according to the RFC.
     *
     * @param base the base directory to which the context is mapped
     * @param context the context which is mapped to the base directory
     * @param req the request
     * @param resp the response into which the content is written
     * @return the HTTP status code to return, or 0 if a response was sent
     * @throws IOException if an error occurs
     */
    public static int serveFile(File base, String context,
            Request req, Response resp) throws IOException {
        String relativePath = req.getPath().substring(context.length());
        File file = new File(base, relativePath).getCanonicalFile();
        if (!file.exists() || file.isHidden()) {
            return 404;
        } else if (!file.canRead()
                   || !file.getPath().startsWith(base.getPath())) { // validate
            return 403;
        } else if (file.isDirectory()) {
            if (relativePath.endsWith("/") || relativePath.length() == 0) {
                if (!req.getVirtualHost().isAllowGeneratedIndex())
                    return 403;
                resp.send(200, createIndex(file, req.getPath()));
            } else { // redirect to the normalized directory URL ending with '/'
                resp.redirect(req.getBaseURL() + req.getPath() + "/", true);
            }
        } else {
            serveFileContent(file, req, resp);
        }
        return 0;
    }

    /**
     * Serves the contents of a file, with its corresponding content types,
     * last modification time, etc. conditional and partial retrievals are
     * handled according to the RFC.
     *
     * @param file the existing and readable file whose contents are served
     * @param req the request
     * @param resp the response into which the content is written
     * @throws IOException if an error occurs
     */
    public static void serveFileContent(File file, Request req, Response resp)
            throws IOException {
        long len = file.length();
        long lastModified = file.lastModified();
        String etag = "W/\"" + lastModified + "\""; // a weak tag based on date
        int status = 200;
        // handle range or conditional request
        long[] range = req.getRange(len);
        if (range == null) {
            status = getConditionalStatus(req, lastModified, etag);
        } else {
            String ifRange = req.getHeaders().get("If-Range");
            if (ifRange == null) {
                if (range[0] >= len)
                    status = 416; // unsatisfiable range
                else
                    status = getConditionalStatus(req, lastModified, etag);
            } else {
                if (range[0] >= len) {
                    // RFC2616#14.16, 10.4.17: invalid If-Range gets everything
                    range = null;
                } else { // send either range or everything
                    if (!ifRange.startsWith("\"")
                            && !ifRange.startsWith("W/")) {
                        Date date = req.getHeaders().getDate("If-Range");
                        if (date != null && lastModified > date.getTime())
                            range = null; // modified - send everything
                    } else if (!ifRange.equals(etag)) {
                        range = null; // modified - send everything
                    }
                }
            }
        }
        // send the response
        Headers respHeaders = resp.getHeaders();
        switch (status) {
            case 304: // no other headers or body allowed
                respHeaders.add("ETag", etag);
                respHeaders.add("Last-Modified", formatDate(lastModified));
                resp.sendHeaders(304);
                break;
            case 412:
                resp.sendHeaders(412);
                break;
            case 416:
                respHeaders.add("Content-Range", "bytes */" + len);
                resp.sendHeaders(416);
                break;
            case 200:
                // send OK response
                resp.sendHeaders(200, len, lastModified, etag,
                    getContentType(file.getName(), "application/octet-stream"),
                    range);
                // send body
                FileInputStream fis = new FileInputStream(file);
                try {
                    resp.sendBody(fis, len, range);
                } finally {
                    fis.close();
                }
                break;
            default:
                resp.sendHeaders(500); // should never happen
                break;
        }
    }

    /**
     * Serves the contents of a directory as an HTML file index.
     *
     * @param dir the existing and readable directory whose contents are served
     * @param path the displayed base path corresponding to dir
     * @return an HTML string containing the file index for the directory
     * @throws IOException if an error occurs
     */
    public static String createIndex(File dir, String path) throws IOException {
        if (!path.endsWith("/"))
            path += "/";
        // calculate name column width
        int w = 21; // minimum width
        for (String name : dir.list())
            if (name.length() > w)
                w = name.length();
        w += 2; // with room for added slash and space
        // note: we use apache's format, for consistent user experience
      @SuppressWarnings("resource")
      Formatter f = new Formatter(Locale.US);
        f.format("<!DOCTYPE html>%n" +
            "<html><head><title>Index of %s</title></head>%n" +
            "<body><h1>Index of %s</h1>%n" +
            "<pre> Name%" + (w - 5) + "s Last modified      Size<hr>",
            path, path, "");
        if (path.length() > 1) // add parent link if not root path
            f.format(" <a href=\"%s/\">Parent Directory</a>%"
                + (w + 5) + "s-%n", getParentPath(path), "");
        for (File file : dir.listFiles()) {
             String name = file.getName() + (file.isDirectory() ? "/" : "");
             String size = file.isDirectory() ? "- " : toSizeApproxString(file.length());
             if (config.httpserver_share_filter == 1 && ! file.isDirectory() &&
                   ! Hlsutils.isVideoFile(file.getName()))
                   continue;
             // properly url-encode the link
             String link = escapeHTML(path + name);
             f.format(" <a href=\"%s\">%s</a>%-" + (w - name.length()) +
                 "s&#8206;%td-%<tb-%<tY %<tR%6s%n",
                 link, name, "", file.lastModified(), size);
        }
        f.format("</pre></body></html>");
        return f.toString();
    }

    /**
     * Starts a stand-alone HTTP server, serving files from disk.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.err.printf("Usage: %s <directory> [port]%n",
                    HTTPServer.class.getName());
                return;
            }
            File dir = new File(args[0]);
            if (!dir.canRead()) {
                System.err.println("error opening " + dir.getAbsolutePath());
                return;
            }
            int port = args.length < 2 ? 80 : Integer.parseInt(args[1]);
            HTTPServer server = new HTTPServer(port);
            VirtualHost host = server.getVirtualHost(null); // default host
            host.setAllowGeneratedIndex(true);
            host.addContext("/", new FileContextHandler(dir, "/"));
            server.start();
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
        }
    }
}
