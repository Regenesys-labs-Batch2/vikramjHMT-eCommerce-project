package com.happiestminds.ecom.payment.service;

import com.happiestminds.ecom.payment.domain.SimulatedOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSimulatorTest {

    final PaymentSimulator simulator = new PaymentSimulator();

    @Test
    @DisplayName("R-PAY-02 an explicit simulated outcome wins over the amount rule")
    void shouldPreferExplicitOutcome() {
        assertThat(simulator.decide(SimulatedOutcome.SUCCESS, new BigDecimal("100.99"))).isEqualTo(SimulatedOutcome.SUCCESS);
        assertThat(simulator.decide(SimulatedOutcome.DECLINED, new BigDecimal("100.00"))).isEqualTo(SimulatedOutcome.DECLINED);
    }

    @Test
    @DisplayName("R-PAY-02 .99 declines, .98 times out, anything else succeeds")
    void shouldDecideByCents() {
        assertThat(simulator.decide(null, new BigDecimal("72999.99"))).isEqualTo(SimulatedOutcome.DECLINED);
        assertThat(simulator.decide(null, new BigDecimal("10.98"))).isEqualTo(SimulatedOutcome.TIMEOUT);
        assertThat(simulator.decide(null, new BigDecimal("55000.00"))).isEqualTo(SimulatedOutcome.SUCCESS);
        assertThat(simulator.decide(null, new BigDecimal("899"))).isEqualTo(SimulatedOutcome.SUCCESS);
    }
}
