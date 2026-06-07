package com.smartwithdraw.currency;

import java.util.Map;

public record WithdrawResult(
int amount,
Map<Integer, Integer> notes
) {
}
