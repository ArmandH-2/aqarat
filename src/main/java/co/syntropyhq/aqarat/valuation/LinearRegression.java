package co.syntropyhq.aqarat.valuation;

import java.util.Arrays;

/**
 * Multiple linear regression, solved with the normal equations: fit finds
 * the coefficient vector b that satisfies (XtX)b = Xty, where X is the
 * feature matrix with a leading column of 1s for the intercept.
 *
 * double is the right type here, not BigDecimal - this is numerical maths
 * on a design matrix, not a money value. Callers convert the prediction to
 * BigDecimal once it becomes a price.
 */
public class LinearRegression {

    private double[] coefficients;

    public void fit(double[][] features, double[] targets) {
        fit(features, targets, uniformWeights(targets.length));
    }

    // Weighted least squares: each row's contribution to the normal
    // equations is scaled by its weight, so (XtWX)b = XtWy. A row with
    // weight 1 for every row is exactly the unweighted fit above.
    public void fit(double[][] features, double[] targets, double[] weights) {
        if (features.length == 0 || features.length != targets.length || features.length != weights.length) {
            throw new IllegalArgumentException(
                "Features, targets and weights must be the same, non-zero length.");
        }
        int columns = features[0].length + 1;
        double[][] xtx = new double[columns][columns];
        double[] xty = new double[columns];

        for (int row = 0; row < features.length; row++) {
            double[] designRow = new double[columns];
            designRow[0] = 1.0;
            System.arraycopy(features[row], 0, designRow, 1, features[row].length);
            double weight = weights[row];
            for (int i = 0; i < columns; i++) {
                xty[i] += weight * designRow[i] * targets[row];
                for (int j = 0; j < columns; j++) {
                    xtx[i][j] += weight * designRow[i] * designRow[j];
                }
            }
        }

        coefficients = solve(xtx, xty);
    }

    public double predict(double[] features) {
        if (coefficients == null) {
            throw new IllegalStateException("Call fit before predict.");
        }
        double result = coefficients[0];
        for (int i = 0; i < features.length; i++) {
            result += coefficients[i + 1] * features[i];
        }
        return result;
    }

    // Index 0 is the intercept, followed by one coefficient per feature
    // column in the order fit() was given them. A clone: callers must not
    // be able to corrupt the fitted model through the returned array.
    public double[] getCoefficients() {
        if (coefficients == null) {
            throw new IllegalStateException("Call fit before getCoefficients.");
        }
        return coefficients.clone();
    }

    private double[] uniformWeights(int rowCount) {
        double[] weights = new double[rowCount];
        Arrays.fill(weights, 1.0);
        return weights;
    }

    // Gaussian elimination with partial pivoting. Without pivoting, a column
    // with little variation - or just an unlucky row order - can divide by a
    // near-zero number and turn one weak feature into silent nonsense across
    // every coefficient, rather than a clear failure.
    private double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }

        for (int pivot = 0; pivot < n; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < n; row++) {
                if (Math.abs(m[row][pivot]) > Math.abs(m[maxRow][pivot])) {
                    maxRow = row;
                }
            }
            double[] swap = m[pivot];
            m[pivot] = m[maxRow];
            m[maxRow] = swap;

            // A near-zero pivot even after choosing the largest candidate
            // means the columns are linearly dependent - for example every
            // property in this slice has the same bedroom count. There is
            // no unique solution, so fail clearly instead of dividing by it.
            if (Math.abs(m[pivot][pivot]) < 1e-10) {
                throw new IllegalStateException(
                    "Cannot fit regression: features are linearly dependent (column " + pivot + ").");
            }

            for (int row = pivot + 1; row < n; row++) {
                double factor = m[row][pivot] / m[pivot][pivot];
                for (int col = pivot; col <= n; col++) {
                    m[row][col] -= factor * m[pivot][col];
                }
            }
        }

        return backSubstitute(m, n);
    }

    private double[] backSubstitute(double[][] m, int n) {
        double[] solution = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double sum = m[row][n];
            for (int col = row + 1; col < n; col++) {
                sum -= m[row][col] * solution[col];
            }
            solution[row] = sum / m[row][row];
        }
        return solution;
    }
}
