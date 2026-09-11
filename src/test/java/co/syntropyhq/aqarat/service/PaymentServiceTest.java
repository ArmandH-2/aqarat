package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.model.PaymentFrequency;
import co.syntropyhq.aqarat.model.PaymentSchedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// PaymentService.buildSchedule() takes plain values and returns a list, no
// database involved - same reasoning as PriceEstimatorTest (CONVENTIONS.md,
// Tests). The DAOs it is otherwise built with are never touched here, so
// null is fine for all of them.
class PaymentServiceTest {

    private final PaymentService paymentService = new PaymentService(null, null, null, null, null, null);

    @Test
    void twelveMonthlyInstallmentsSumToTheTotalWithNoDriftOnTheLastRow() {
        BigDecimal total = new BigDecimal("10000.00");
        List<PaymentSchedule> schedule = paymentService.buildSchedule(
            PaymentFrequency.MONTHLY, 12, total, LocalDate.of(2026, 1, 1));

        assertEquals(12, schedule.size());
        assertEquals(0, total.compareTo(sumOf(schedule)));
        // 10000 / 12 = 833.33 with 4 cents left over, all on the last row.
        assertEquals(0, new BigDecimal("833.33").compareTo(schedule.get(0).getAmountDue()));
        assertEquals(0, new BigDecimal("833.37").compareTo(schedule.get(11).getAmountDue()));
    }

    @Test
    void aTotalThatDoesNotDivideEvenlyStillSumsExactly() {
        BigDecimal total = new BigDecimal("1000.00");
        List<PaymentSchedule> schedule = paymentService.buildSchedule(
            PaymentFrequency.INSTALLMENT, 3, total, LocalDate.of(2026, 1, 1));

        assertEquals(0, new BigDecimal("333.33").compareTo(schedule.get(0).getAmountDue()));
        assertEquals(0, new BigDecimal("333.33").compareTo(schedule.get(1).getAmountDue()));
        assertEquals(0, new BigDecimal("333.34").compareTo(schedule.get(2).getAmountDue()));
        assertEquals(0, total.compareTo(sumOf(schedule)));
    }

    @Test
    void oneOffGivesExactlyOneRowForTheFullAmount() {
        BigDecimal total = new BigDecimal("250000.00");
        List<PaymentSchedule> schedule = paymentService.buildSchedule(
            PaymentFrequency.ONE_OFF, 6, total, LocalDate.of(2026, 3, 1));

        assertEquals(1, schedule.size());
        assertEquals(0, total.compareTo(schedule.get(0).getAmountDue()));
        assertEquals(LocalDate.of(2026, 3, 1), schedule.get(0).getDueDate());
    }

    @Test
    void dueDatesStepByOneThreeAndTwelveMonthsPerFrequency() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        BigDecimal total = new BigDecimal("300.00");

        List<PaymentSchedule> monthly =
            paymentService.buildSchedule(PaymentFrequency.MONTHLY, 3, total, start);
        assertEquals(LocalDate.of(2026, 2, 15), monthly.get(1).getDueDate());
        assertEquals(LocalDate.of(2026, 3, 15), monthly.get(2).getDueDate());

        List<PaymentSchedule> quarterly =
            paymentService.buildSchedule(PaymentFrequency.QUARTERLY, 3, total, start);
        assertEquals(LocalDate.of(2026, 4, 15), quarterly.get(1).getDueDate());
        assertEquals(LocalDate.of(2026, 7, 15), quarterly.get(2).getDueDate());

        List<PaymentSchedule> annual =
            paymentService.buildSchedule(PaymentFrequency.ANNUAL, 3, total, start);
        assertEquals(LocalDate.of(2027, 1, 15), annual.get(1).getDueDate());
        assertEquals(LocalDate.of(2028, 1, 15), annual.get(2).getDueDate());
    }

    @Test
    void installmentNumbersStartAtOneAndCountUp() {
        List<PaymentSchedule> schedule = paymentService.buildSchedule(
            PaymentFrequency.INSTALLMENT, 4, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1));

        for (int i = 0; i < schedule.size(); i++) {
            assertEquals(i + 1, schedule.get(i).getInstallmentNo());
        }
    }

    private BigDecimal sumOf(List<PaymentSchedule> schedule) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentSchedule row : schedule) {
            sum = sum.add(row.getAmountDue());
        }
        return sum;
    }
}
