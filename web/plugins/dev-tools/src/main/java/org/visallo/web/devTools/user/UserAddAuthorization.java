package org.visallo.web.devTools.user;

import com.google.inject.Inject;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserPropertyAuthorizationRepository;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.visallo.webster.ParameterizedHandler;
import org.json.JSONObject;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;

public class UserAddAuthorization implements ParameterizedHandler {
    private final UserRepository userRepository;
    private final UserPropertyAuthorizationRepository userPropertyAuthorizationRepository;

    @Inject
    public UserAddAuthorization(final UserRepository userRepository, AuthorizationRepository authorizationRepository, UserPropertyAuthorizationRepository userPropertyAuthorizationRepository) {

        this.userRepository = userRepository;
        this.userPropertyAuthorizationRepository = userPropertyAuthorizationRepository;
    }

    @Handle
    public JSONObject handle(
            @Required(name = "user-name") String userName,
            @Required(name = "auth") String auth,
            User authUser
    ) throws Exception {
        User user = userRepository.findByUsername(userName);
        if (user == null) {
            throw new VisalloResourceNotFoundException("User " + userName + " not found");
        }
        userPropertyAuthorizationRepository.addAuthorization(user,auth,authUser);
        //userRepository.addAuthorization(user, auth, authUser);
        return userRepository.toJsonWithAuths(user);
    }
}
