package dev.godzilla.city;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * tokyo.gzc decoded once and kept in memory: the palette as parsed BlockStates plus the cell index array (one byte per cell when
 * the palette has at most 256 entries, else a char). Cell order: y outermost, then z, then x.
 */
final class CityFile {
	static final String RESOURCE = "/data/godzilla/city/tokyo.gzc";

	final int sizeX, sizeY, sizeZ;
	final int originX, originY, originZ;
	final String[] paletteNames;
	/** Parsed palette; an entry is null when its string could not be parsed (see {@link #errors}). */
	final BlockState[] palette;
	private final byte[] small;
	private final char[] large;
	/** MD5 of the packed file: keys the stamp marker and the light cache. */
	final String hash;
	final List<String> errors;

	private CityFile(int[] dims, String[] names, BlockState[] palette, byte[] small, char[] large, String hash, List<String> errors) {
		this.sizeX = dims[0];
		this.sizeY = dims[1];
		this.sizeZ = dims[2];
		this.originX = dims[3];
		this.originY = dims[4];
		this.originZ = dims[5];
		this.paletteNames = names;
		this.palette = palette;
		this.small = small;
		this.large = large;
		this.hash = hash;
		this.errors = errors;
	}

	boolean usable() {
		return errors.isEmpty();
	}

	long cellCount() {
		return (long) sizeX * sizeY * sizeZ;
	}

	/** Palette index of cell number i (y, z, x order). */
	int get(int i) {
		return small != null ? small[i] & 0xFF : large[i];
	}

	long memoryBytes() {
		return small != null ? small.length : large.length * 2L;
	}

	/** Null when the jar has no city file. Throws IOException on a corrupt file. */
	static CityFile load() throws IOException {
		byte[] packed;
		try (InputStream in = CityFile.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				return null;
			}
			packed = in.readAllBytes();
		}
		String hash;
		try {
			hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(packed));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(packed)), 1 << 16))) {
			byte[] magic = new byte[4];
			in.readFully(magic);
			if (!"GZC1".equals(new String(magic, StandardCharsets.US_ASCII))) {
				throw new IOException("bad magic in tokyo.gzc (expected GZC1)");
			}
			int[] dims = new int[6];
			for (int i = 0; i < 6; i++) {
				dims[i] = Integer.reverseBytes(in.readInt());
			}
			long cells = (long) dims[0] * dims[1] * dims[2];
			if (dims[0] <= 0 || dims[1] <= 0 || dims[2] <= 0 || cells > Integer.MAX_VALUE) {
				throw new IOException("bad size in tokyo.gzc: " + dims[0] + "x" + dims[1] + "x" + dims[2]);
			}
			int paletteCount = Short.reverseBytes(in.readShort()) & 0xFFFF;
			String[] names = new String[paletteCount];
			for (int i = 0; i < paletteCount; i++) {
				int len = Short.reverseBytes(in.readShort()) & 0xFFFF;
				byte[] b = new byte[len];
				in.readFully(b);
				names[i] = new String(b, StandardCharsets.UTF_8);
			}
			List<String> errors = new ArrayList<>();
			BlockState[] palette = new BlockState[paletteCount];
			for (int i = 0; i < paletteCount; i++) {
				try {
					palette[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, names[i], false).blockState();
				} catch (CommandSyntaxException | RuntimeException e) {
					errors.add("palette entry " + i + " \"" + names[i] + "\": " + e.getMessage());
				}
			}
			if (paletteCount == 0 || !"minecraft:air".equals(names[0])) {
				errors.add("palette index 0 must be minecraft:air");
			}
			int n = (int) cells;
			byte[] small = paletteCount <= 256 ? new byte[n] : null;
			char[] large = small == null ? new char[n] : null;
			byte[] buf = new byte[1 << 16];
			int cell = 0;
			int bad = 0;
			while (cell < n) {
				int cellsNow = Math.min(buf.length / 2, n - cell);
				in.readFully(buf, 0, cellsNow * 2);
				for (int k = 0; k < cellsNow; k++) {
					int v = (buf[2 * k] & 0xFF) | (buf[2 * k + 1] & 0xFF) << 8;
					if (v >= paletteCount) {
						bad++;
						v = 0;
					}
					if (small != null) {
						small[cell + k] = (byte) v;
					} else {
						large[cell + k] = (char) v;
					}
				}
				cell += cellsNow;
			}
			if (bad > 0) {
				errors.add(bad + " cells have a palette index >= " + paletteCount);
			}
			return new CityFile(dims, names, palette, small, large, hash, errors);
		}
	}
}
