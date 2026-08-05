package co.syntropyhq.aqarat.valuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinearRegressionTest {

    @Test
    void recoversKnownCoefficients() {
        // Generated from y = 5 + 2*x0 + 3*x1, noiseless, five rows for three
        // unknowns - the normal equations should recover the line exactly.
        double[][] features = {
            { 0, 0 },
            { 1, 0 },
            { 0, 1 },
            { 1, 1 },
            { 2, 1 },
        };
        double[] targets = { 5, 7, 8, 10, 12 };

        LinearRegression regression = new LinearRegression();
        regression.fit(features, targets);

        double predicted = regression.predict(new double[] { 2, 3 });
        assertEquals(18.0, predicted, 1e-6, "5 + 2*2 + 3*3 = 18");
    }

    @Test
    void refusesToFitWhenAColumnHasNoVariation() {
        // x1 is 5 in every row, so it is a constant multiple of the
        // intercept column - the columns are linearly dependent.
        double[][] features = {
            { 1, 5 },
            { 2, 5 },
            { 3, 5 },
        };
        double[] targets = { 10, 20, 30 };

        LinearRegression regression = new LinearRegression();
        assertThrows(IllegalStateException.class, () -> regression.fit(features, targets));
    }
}
