package plumLinking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LinkingTools {

	public static List<Map<String, String>> filterMapsByCriteria(List<Map<String, String>> listMaps,
			HashMap<String, Set<String>> hash) {
		List<Map<String, String>> returnlistMaps = new ArrayList<>();
		listMaps.forEach(map -> {
			boolean tmp1 = true;
			for (String key : hash.keySet()) {
				boolean tmp0 = false;
				for (String v : hash.get(key)) {
					if (map.get(key).equals(v)) {
						tmp0 = true;
						break;
					}
				}
				tmp1 = tmp0;
				if (!tmp1) {
					break;
				}
			}
			if (tmp1)
				returnlistMaps.add(map);
		});
		return returnlistMaps;
	}

	public static List<Map<String, String>> left_join(List<Map<String, String>> csv1, List<Map<String, String>> csv2,
			String... keys) {
		List<Map<String, String>> result = new ArrayList<>();

		// Iterate through each row in csv1
		for (Map<String, String> row1 : csv1) {
			boolean matchFound = false;

			// Iterate through each row in csv2 to find a match based on keys
			for (Map<String, String> row2 : csv2) {
				boolean isMatch = true;

				// Check if all keys match between the two rows
				for (String key : keys) {
					if (!Objects.equals(row1.get(key), row2.get(key))) {
						isMatch = false;
						break;
					}
				}

				if (isMatch) {
					matchFound = true;
					// Create a new row by merging row1 and row2
					Map<String, String> mergedRow = new HashMap<>(row1);
					for (Map.Entry<String, String> entry : row2.entrySet()) {
						// Avoid overwriting existing keys from row1
						if (!mergedRow.containsKey(entry.getKey()) || !Arrays.asList(keys).contains(entry.getKey())) {
							mergedRow.put(entry.getKey(), entry.getValue());
						}
					}
					result.add(mergedRow);
				}
			}

			// If no match was found, add row1 as it is (with null values for csv2's
			// columns)
			if (!matchFound) {
				result.add(new HashMap<>(row1));
			}
		}

		return result;
	}

	public static List<Map<String, String>> left_join_many_to_many(List<Map<String, String>> csv1,
			List<Map<String, String>> csv2, String... keys) {
		List<Map<String, String>> result = new ArrayList<>();

		// Iterate through each row in csv1
		for (Map<String, String> row1 : csv1) {
			boolean matchFound = false;

			// Iterate through each row in csv2 to find all matches based on keys
			for (Map<String, String> row2 : csv2) {
				boolean isMatch = true;

				// Check if all key values match between row1 and row2
				for (String key : keys) {
					if (!Objects.equals(row1.get(key), row2.get(key))) {
						isMatch = false;
						break;
					}
				}

				// If there is a match, merge the two rows
				if (isMatch) {
					matchFound = true;
					Map<String, String> mergedRow = new HashMap<>(row1);
					for (Map.Entry<String, String> entry : row2.entrySet()) {
						// Avoid overwriting existing keys from row1 unless they are not join keys
						if (!mergedRow.containsKey(entry.getKey()) || !Arrays.asList(keys).contains(entry.getKey())) {
							mergedRow.put(entry.getKey(), entry.getValue());
						}
					}
					result.add(mergedRow); // Add merged row to result
				}
			}

			// If no match is found, add row1 to result with nulls for missing csv2 columns
			if (!matchFound) {
				Map<String, String> noMatchRow = new HashMap<>(row1);

				// Add keys from csv2 to ensure all columns are present in the output
				for (Map<String, String> row2 : csv2) {
					for (String key : row2.keySet()) {
						if (!noMatchRow.containsKey(key)) {
							noMatchRow.put(key, null);
						}
					}
				}

				result.add(noMatchRow);
			}
		}
		return result;
	}
}
