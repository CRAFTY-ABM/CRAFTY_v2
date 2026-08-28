package de.cesr.crafty.core.dataLoader.land;

import java.util.Comparator;

/**
 * Metadata controlling how a land-use mask is applied.
 *
 * @param name mask name; must match the LandUseControl folder name
 * @param forced whether the winning mask immediately forces an AFT owner
 * @param priority smaller values have higher priority
 * @param fileOrder zero-based row order in LandUseControl-metadata.csv
 */
public record MaskMetadata(String name, boolean forced, int priority, int fileOrder) {

	public static final Comparator<MaskMetadata> PRIORITY_ORDER = Comparator.comparingInt(MaskMetadata::priority)
			.thenComparingInt(MaskMetadata::fileOrder).thenComparing(MaskMetadata::name,
					String.CASE_INSENSITIVE_ORDER);
}
