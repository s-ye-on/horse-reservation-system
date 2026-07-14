package com.horse.reservations.presentation;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class PendingPaymentExpirySchedulingConfiguration {
}
