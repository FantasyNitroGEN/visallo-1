package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.parameterProviders.ParameterProvider;
import org.visallo.webster.parameterProviders.ParameterProviderFactory;
import org.vertexium.Authorizations;
import org.visallo.core.config.Configuration;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Singleton
public class AuthorizationsParameterProviderFactory extends ParameterProviderFactory<Authorizations> {
    private final ParameterProvider<Authorizations> parameterProvider;

    @Inject
    public AuthorizationsParameterProviderFactory(
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            Configuration configuration,
            AuthorizationRepository authorizationRepository
    ) {
        parameterProvider = new VisalloBaseParameterProvider<Authorizations>(userRepository, configuration) {
            @Override
            public Authorizations getParameter(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    HandlerChain chain
            ) {
                return getAuthorizations(request, getUserRepository(), authorizationRepository, workspaceRepository);
            }
        };
    }

    public static Authorizations getAuthorizations(
            HttpServletRequest request,
            UserRepository userRepository,
            AuthorizationRepository authorizationRepository,
            WorkspaceRepository workspaceRepository
    ) {
        User user = VisalloBaseParameterProvider.getUser(request, userRepository);
        if (user == null) {
            return null;
        }
        String workspaceId = VisalloBaseParameterProvider.getActiveWorkspaceIdOrDefault(request, workspaceRepository, userRepository);
        if (workspaceId != null) {
            return authorizationRepository.getGraphAuthorizations(user, workspaceId);
        }

        return authorizationRepository.getGraphAuthorizations(user);
    }

    @Override
    public boolean isHandled(
            Method handleMethod,
            Class<? extends Authorizations> parameterType,
            Annotation[] parameterAnnotations
    ) {
        return Authorizations.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<Authorizations> createParameterProvider(
            Method handleMethod,
            Class<?> parameterType,
            Annotation[] parameterAnnotations
    ) {
        return parameterProvider;
    }
}
