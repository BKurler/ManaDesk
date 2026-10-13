/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Copies a .tar.gz and makes the named entries executable (mode 755) - nothing
 * else changes: every header and data block is copied byte for byte, only the
 * mode field (and the header checksum) of those entries is rewritten.
 * <p>
 * Release archives built on Windows lose the executable flag (Windows has
 * none), so on Linux / macOS the launcher would not start. Run by releases.xml
 * with the java launcher (a single source file - nothing to compile):
 *
 * <pre>
 * java FixTarModes.java in.tar.gz out.tar.gz ManaDesk/manadesk ManaDesk/manadesk.sh
 * </pre>
 *
 * Exit code 0 on success, 1 on error (the release copy is then left out).
 */
public class FixTarModes {
	private static final int BLOCK = 512;

	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("usage: FixTarModes in.tar.gz out.tar.gz entry...");
			System.exit(1);
		}
		File in = new File(args[0]);
		File out = new File(args[1]);
		Set<String> exec = new HashSet<>(Arrays.asList(args).subList(2, args.length));
		File tmp = new File(out.getPath() + ".tmp");
		try {
			int fixed = copy(in, tmp, exec);
			Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
			System.out.println("FixTarModes: " + out.getName() + " - " + fixed + " of " + exec.size()
					+ " entries made executable");
			if (fixed < exec.size())
				System.out.println("FixTarModes: WARNING - not every entry was found in " + in.getName());
		} catch (IOException e) {
			tmp.delete();
			System.err.println("FixTarModes: " + in.getName() + ": " + e);
			System.exit(1);
		}
	}

	static int copy(File inFile, File outFile, Set<String> exec) throws IOException {
		int fixed = 0;
		try (InputStream in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(inFile), 65536));
				OutputStream out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)),
						65536)) {
			byte[] header = new byte[BLOCK];
			byte[] buf = new byte[65536];
			while (readBlock(in, header)) {
				if (allZero(header)) {
					// end of the archive: the rest (end blocks, padding) as is
					out.write(header);
					for (int n; (n = in.read(buf)) > 0;)
						out.write(buf, 0, n);
					break;
				}
				char type = (char) header[156];
				String name = string(header, 0, 100);
				String prefix = string(header, 345, 155); // ustar
				String path = prefix.isEmpty() ? name : prefix + "/" + name;
				if ((type == '0' || type == 0) && exec.contains(path)) {
					setMode(header, "0000755");
					fixed++;
				}
				out.write(header);
				long size = octal(header, 124, 12);
				long remaining = (size + BLOCK - 1) / BLOCK * BLOCK;
				while (remaining > 0) {
					int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
					if (n < 0)
						throw new IOException("truncated archive");
					out.write(buf, 0, n);
					remaining -= n;
				}
			}
		}
		return fixed;
	}

	private static boolean readBlock(InputStream in, byte[] block) throws IOException {
		int off = 0;
		while (off < block.length) {
			int n = in.read(block, off, block.length - off);
			if (n < 0) {
				if (off == 0)
					return false;
				throw new IOException("truncated header");
			}
			off += n;
		}
		return true;
	}

	private static boolean allZero(byte[] b) {
		for (byte x : b)
			if (x != 0)
				return false;
		return true;
	}

	private static String string(byte[] b, int off, int len) {
		int end = off;
		while (end < off + len && b[end] != 0)
			end++;
		return new String(b, off, end - off, StandardCharsets.UTF_8);
	}

	private static long octal(byte[] b, int off, int len) throws IOException {
		if ((b[off] & 0x80) != 0)
			throw new IOException("entry too large");
		long v = 0;
		for (int i = off; i < off + len; i++) {
			byte c = b[i];
			if (c == 0 || c == ' ') {
				if (v > 0)
					break;
				continue;
			}
			if (c < '0' || c > '7')
				throw new IOException("bad header");
			v = v * 8 + (c - '0');
		}
		return v;
	}

	/** Writes the 8-byte mode field and recomputes the header checksum. */
	private static void setMode(byte[] h, String mode) {
		byte[] m = (mode + "\0").getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(m, 0, h, 100, 8);
		Arrays.fill(h, 148, 156, (byte) ' ');
		long sum = 0;
		for (byte x : h)
			sum += x & 0xff;
		byte[] c = String.format("%06o", sum).getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(c, 0, h, 148, 6);
		h[154] = 0;
		h[155] = ' ';
	}
}
