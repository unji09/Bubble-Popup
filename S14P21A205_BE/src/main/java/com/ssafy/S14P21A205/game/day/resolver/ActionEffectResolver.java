package com.ssafy.S14P21A205.game.day.resolver;

import com.ssafy.S14P21A205.action.entity.ActionCategory;
import com.ssafy.S14P21A205.action.entity.ActionLog;
import com.ssafy.S14P21A205.action.entity.PromotionType;
import java.math.BigDecimal;
import java.util.List;

public class ActionEffectResolver {

    private static final BigDecimal DECIMAL_ZERO = new BigDecimal("0.00");
    private static final BigDecimal DECIMAL_ONE = new BigDecimal("1.00");

    public ActionEffect resolve(List<ActionLog> actionLogs) {
        boolean discountUsed = false;
        boolean donationUsed = false;
        boolean influencerUsed = false;
        boolean snsUsed = false;
        boolean leafletUsed = false;
        boolean friendUsed = false;
        long totalCost = 0L;
        BigDecimal captureRateMultiplier = DECIMAL_ONE;

        for (ActionLog actionLog : actionLogs) {
            if (actionLog.getAction() == null) {
                continue;
            }

            totalCost += actionLog.getAction().getCost() == null ? 0L : actionLog.getAction().getCost();

            ActionCategory category = actionLog.getAction().getCategory();
            if (category == ActionCategory.DISCOUNT) {
                captureRateMultiplier = captureRateMultiplier.multiply(normalizeMultiplier(actionLog.getActionValue()));
            } else if (category == ActionCategory.DONATION) {
                captureRateMultiplier = captureRateMultiplier.multiply(toBonusMultiplier(actionLog.getActionValue()));
            } else if (category == ActionCategory.PROMOTION) {
                captureRateMultiplier = captureRateMultiplier.multiply(
                        normalizeMultiplier(actionLog.getAction().getCaptureRate())
                );
            } else {
                captureRateMultiplier = captureRateMultiplier.multiply(
                        toBonusMultiplier(actionLog.getAction().getCaptureRate())
                );
            }

            if (category == ActionCategory.DISCOUNT) {
                discountUsed = true;
                continue;
            }
            if (category == ActionCategory.DONATION) {
                donationUsed = true;
                continue;
            }
            if (category != ActionCategory.PROMOTION) {
                continue;
            }

            PromotionType promotionType = actionLog.getAction().getPromotionType();
            if (promotionType == PromotionType.INFLUENCER) {
                influencerUsed = true;
            } else if (promotionType == PromotionType.SNS) {
                snsUsed = true;
            } else if (promotionType == PromotionType.LEAFLET) {
                leafletUsed = true;
            } else if (promotionType == PromotionType.FRIEND) {
                friendUsed = true;
            }
        }

        return new ActionEffect(
                discountUsed,
                donationUsed,
                influencerUsed,
                snsUsed,
                leafletUsed,
                friendUsed,
                totalCost,
                captureRateMultiplier
        );
    }

    private BigDecimal normalizeMultiplier(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return DECIMAL_ONE;
        }
        return value;
    }

    private BigDecimal toBonusMultiplier(BigDecimal value) {
        BigDecimal bonus = value == null ? DECIMAL_ZERO : value;
        if (bonus.signum() <= 0) {
            return DECIMAL_ONE;
        }
        return DECIMAL_ONE.add(bonus);
    }

    public record ActionEffect(
            boolean discountUsed,
            boolean donationUsed,
            boolean influencerUsed,
            boolean snsUsed,
            boolean leafletUsed,
            boolean friendUsed,
            long totalCost,
            BigDecimal captureRateMultiplier
    ) {
    }
}
