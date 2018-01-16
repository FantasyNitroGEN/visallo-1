package org.visallo.web.privilegeFilters;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.model.user.PrivilegeRepository;
import org.visallo.web.clientapi.model.Privilege;

@Singleton
public class HistoryReadPrivilegeFilter extends PrivilegeFilter {
    @Inject
    protected HistoryReadPrivilegeFilter(PrivilegeRepository privilegeRepository) {
        super(Privilege.newSet(Privilege.READ, Privilege.HISTORY_READ), privilegeRepository);
    }
}
