package newCoupler;

import java.util.*;

/**
 * Computes a regularized left-inverse A of M: A = (M^T M + lambda I)^(-1) M^T
 *
 * Input: M: Map<rowKey, Map<colKey, value>> (n x m) C: Map<colKey, value> (used
 * mainly to define/keep the column set/order; values not required for
 * A-approach)
 *
 * Output: A: Map<colKey, Map<rowKey, value>> (m x n)
 */
public final class LeftInverse {

	private static final double EPS = 1e-12;

	private LeftInverse() {
	}

	public static Map<String, Map<String, Double>> leftInverseRidge(String country,Map<String, Map<String, Double>> M,
			Map<String, Double> C) {
		double lambda = 1e-12;
		if (M == null || M.isEmpty())
			throw new IllegalArgumentException("M is null/empty");
		if (C == null || C.isEmpty())
			throw new IllegalArgumentException("C is null/empty (need column keys)");
		if (!(lambda > 0.0))
			throw new IllegalArgumentException("lambda must be > 0");

		// Deterministic ordering
		List<String> rowKeys = new ArrayList<>(new TreeSet<>(M.keySet())); // S-keys (n)
		List<String> colKeys = new ArrayList<>(new TreeSet<>(C.keySet())); // C-keys (m)

		final int n = rowKeys.size();
		final int m = colKeys.size();

		// Build dense matrix M_dense[n][m]
		double[][] Md = new double[n][m];
		for (int i = 0; i < n; i++) {
			String r = rowKeys.get(i);
			Map<String, Double> row = M.get(r);
			if (row == null)
				continue;
			for (int j = 0; j < m; j++) {
				String c = colKeys.get(j);
				Double v = row.get(c);
				if (v != null)
					Md[i][j] = v;
			}
		}

		// Compute MtM = M^T M (m x m)
		double[][] MtM = new double[m][m];
		for (int i = 0; i < n; i++) {
			for (int a = 0; a < m; a++) {
				double Mia = Md[i][a];
				if (Math.abs(Mia) < EPS)
					continue;
				for (int b = 0; b < m; b++) {
					double Mib = Md[i][b];
					if (Math.abs(Mib) < EPS)
						continue;
					MtM[a][b] += Mia * Mib;
				}
			}
		}

		// Add ridge: MtM + lambda I
		for (int k = 0; k < m; k++)
			MtM[k][k] += lambda;

		// Invert (MtM + lambda I)
		double[][] inv = invertGaussJordan(country,MtM);

		// A = inv * M^T => A[m][n], with A[p][i] = sum_q inv[p][q] * Md[i][q]
		double[][] A = new double[m][n];
		for (int p = 0; p < m; p++) {
			for (int i = 0; i < n; i++) {
				double sum = 0.0;
				for (int q = 0; q < m; q++) {
					double ipq = inv[p][q];
					if (Math.abs(ipq) < EPS)
						continue;
					double miq = Md[i][q];
					if (Math.abs(miq) < EPS)
						continue;
					sum += ipq * miq;
				}
				A[p][i] = sum;
			}
		}

		// Convert to Map<colKey, Map<rowKey, value>>
		Map<String, Map<String, Double>> out = new HashMap<>();
		for (int p = 0; p < m; p++) {
			String colKey = colKeys.get(p);
			Map<String, Double> inner = new HashMap<>();
			for (int i = 0; i < n; i++) {
				double v = A[p][i];
				if (Math.abs(v) > EPS) {
					inner.put(rowKeys.get(i), v);
				}
			}
			out.put(colKey, inner);
		}

		return out;
	}

	/**
	 * Gauss-Jordan inversion with partial pivoting. Assumes matrix is square and
	 * invertible (ridge with lambda>0 usually ensures that).
	 */
	private static double[][] invertGaussJordan(String country,double[][] A) {
		int n = A.length;
		if (n == 0 || A[0].length != n)
			throw new IllegalArgumentException("Matrix must be square");

		// Augment [A | I]
		double[][] aug = new double[n][2 * n];
		for (int i = 0; i < n; i++) {
			if (A[i].length != n)
				throw new IllegalArgumentException("Matrix must be square");
			System.arraycopy(A[i], 0, aug[i], 0, n);
			aug[i][n + i] = 1.0;
		}

		// Eliminate
		for (int col = 0; col < n; col++) {
			// Pivot row
			int pivot = col;
			double best = Math.abs(aug[col][col]);
			for (int r = col + 1; r < n; r++) {
				double v = Math.abs(aug[r][col]);
				if (v > best) {
					best = v;
					pivot = r;
				}
			}
			if (best < EPS) {
//				for (int i = 0; i < A.length; i++) {
//					System.out.println(Arrays.toString(A[i]));
//				}
				System.out.println("WARN: "+country +" Matrix is singular/ill-conditioned even after regularization." );
//		
			}

			// Swap
			if (pivot != col) {
				double[] tmp = aug[col];
				aug[col] = aug[pivot];
				aug[pivot] = tmp;
			}

			// Normalize pivot row
			double diag = aug[col][col];
			for (int j = 0; j < 2 * n; j++)
				aug[col][j] /= diag;

			// Eliminate other rows
			for (int r = 0; r < n; r++) {
				if (r == col)
					continue;
				double factor = aug[r][col];
				if (Math.abs(factor) < EPS)
					continue;
				for (int j = 0; j < 2 * n; j++) {
					aug[r][j] -= factor * aug[col][j];
				}
			}
		}

		// Extract inverse
		double[][] inv = new double[n][n];
		for (int i = 0; i < n; i++) {
			System.arraycopy(aug[i], n, inv[i], 0, n);
		}
		return inv;
	}
}