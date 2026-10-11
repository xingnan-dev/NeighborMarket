package com.neighbormarket.service;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.ItemInterestMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class PersonalExchangeService {
    private final ExchangeSupport support;
    private final ItemInterestMapper interests;
    private final HandoverWorkflowService handovers;

    PersonalExchangeService(ExchangeSupport support, ItemInterestMapper interests,
                            HandoverWorkflowService handovers) {
        this.support = support;
        this.interests = interests;
        this.handovers = handovers;
    }

    ExchangeService.Dashboard dashboard(String username) {
        AppUser user = support.user(username);
        List<ExchangeService.InterestView> mine = interests.selectViewsByUser(user.getId())
                .stream().map(support::interestView).toList();
        return new ExchangeService.Dashboard(mine, handovers.viewsByUser(user.getId()));
    }
}
