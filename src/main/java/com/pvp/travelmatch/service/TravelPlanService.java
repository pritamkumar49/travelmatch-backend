package com.pvp.travelmatch.service;

import com.pvp.travelmatch.entity.TravelPartner;
import com.pvp.travelmatch.entity.User;
import com.pvp.travelmatch.repository.TravelPartnerRepository;
import com.pvp.travelmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TravelPartnerService {
    public final UserRepository userRepository;
    public final TravelPartnerRepository travelPartnerRepository;
    public List<TravelPartner> getMyPartners() {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow();

        return travelPartnerRepository.findByUserOneOrUserTwo(user, user);
    }
}
