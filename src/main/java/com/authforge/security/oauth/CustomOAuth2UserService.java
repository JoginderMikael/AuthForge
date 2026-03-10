package com.authforge.security.oauth;

import com.authforge.entity.Role;
import com.authforge.entity.SocialAccount;
import com.authforge.entity.User;
import com.authforge.repository.RoleRepository;
import com.authforge.repository.SocialAccountRepository;
import com.authforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        
        return processOAuth2User(registrationId, oAuth2User);
    }

    private OAuth2User processOAuth2User(String registrationId, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = getEmail(registrationId, attributes);
        String providerId = oAuth2User.getName(); // Usually the internal ID of the provider

        log.debug("Processing OAuth2 login for {} with email {}", registrationId, email);

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            updateExistingUser(user, registrationId, providerId);
        } else {
            user = registerNewUser(registrationId, providerId, email, attributes);
        }

        return new DefaultOAuth2User(
                user.getRoles().stream()
                        .map(role -> (org.springframework.security.core.GrantedAuthority) () -> role.getName())
                        .collect(java.util.stream.Collectors.toSet()),
                attributes,
                userRequestAttributeName(registrationId));
    }

    private String getEmail(String registrationId, Map<String, Object> attributes) {
        if ("github".equalsIgnoreCase(registrationId)) {
            return (String) attributes.get("email");
        }
        return (String) attributes.get("email");
    }

    private String userRequestAttributeName(String registrationId) {
        if ("github".equalsIgnoreCase(registrationId)) {
            return "id";
        }
        return "sub";
    }

    private User registerNewUser(String registrationId, String providerId, String email, Map<String, Object> attributes) {
        log.info("Registering new OAuth2 user: {}", email);
        
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default Role not found"));

        User user = User.builder()
                .email(email)
                .password("") // Social users don't have a local password by default
                .firstName(getFirstName(registrationId, attributes))
                .lastName(getLastName(registrationId, attributes))
                .enabled(true)
                .roles(Set.of(userRole))
                .build();

        user = userRepository.save(user);

        SocialAccount socialAccount = SocialAccount.builder()
                .provider(registrationId.toUpperCase())
                .providerId(providerId)
                .user(user)
                .build();

        socialAccountRepository.save(socialAccount);
        return user;
    }

    private void updateExistingUser(User user, String registrationId, String providerId) {
        Optional<SocialAccount> socialAccountOptional = socialAccountRepository
                .findByUserAndProvider(user, registrationId.toUpperCase());

        if (socialAccountOptional.isEmpty()) {
            SocialAccount socialAccount = SocialAccount.builder()
                    .provider(registrationId.toUpperCase())
                    .providerId(providerId)
                    .user(user)
                    .build();
            socialAccountRepository.save(socialAccount);
            log.info("Linked existing user {} to social account {}", user.getEmail(), registrationId);
        }
    }

    private String getFirstName(String registrationId, Map<String, Object> attributes) {
        if ("github".equalsIgnoreCase(registrationId)) {
            String name = (String) attributes.get("name");
            return name != null ? name.split(" ")[0] : (String) attributes.get("login");
        }
        return (String) attributes.get("given_name");
    }

    private String getLastName(String registrationId, Map<String, Object> attributes) {
        if ("github".equalsIgnoreCase(registrationId)) {
            String name = (String) attributes.get("name");
            if (name != null && name.contains(" ")) {
                return name.substring(name.indexOf(" ") + 1);
            }
            return "";
        }
        return (String) attributes.get("family_name");
    }
}
