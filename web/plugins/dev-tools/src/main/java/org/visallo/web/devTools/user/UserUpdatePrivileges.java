package org.visallo.web.devTools.user;

import com.google.inject.Inject;
import org.visallo.core.model.user.UserPropertyAuthorizationRepository;
import org.visallo.core.model.user.UserPropertyPrivilegeRepository;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.visallo.webster.ParameterizedHandler;
import org.json.JSONObject;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.Privilege;

import java.util.Set;

public class UserUpdatePrivileges implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(UserUpdatePrivileges.class);
    private final UserRepository userRepository;
    private final UserPropertyPrivilegeRepository userPropertyPrivilegeRepository;

    @Inject
    public UserUpdatePrivileges(final UserRepository userRepository, UserPropertyPrivilegeRepository userPropertyPrivilegeRepository) {
        this.userRepository = userRepository;
        this.userPropertyPrivilegeRepository = userPropertyPrivilegeRepository;
    }

    @Handle
    public JSONObject handle(
            @Required(name = "user-name") String userName,
            @Required(name = "privileges") String privilegesParameter,
            User authUser
    ) throws Exception {
        Set<String> privileges = Privilege.stringToPrivileges(privilegesParameter);
        //Set<Privilege> privileges = Privilege.stringToPrivileges(privilegesParameter);

        User user = userRepository.findByUsername(userName);
        if (user == null) {
            throw new VisalloResourceNotFoundException("Could not find user: " + userName);
        }

        LOGGER.info("Setting user %s privileges to %s", user.getUserId(), Privilege.toString(privileges));
        userPropertyPrivilegeRepository.setPrivileges(user, privileges, authUser);
        //userRepository.setPrivileges(user, privileges, authUser);

        return userRepository.toJsonWithAuths(user);
    }
}
