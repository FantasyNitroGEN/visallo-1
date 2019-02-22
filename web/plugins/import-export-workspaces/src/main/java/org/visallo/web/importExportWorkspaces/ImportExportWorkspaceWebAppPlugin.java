package org.visallo.web.importExportWorkspaces;

import org.visallo.webster.Handler;
import org.visallo.webster.handlers.StaticResourceHandler;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.web.VisalloCsrfHandler;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;
import org.visallo.web.privilegeFilters.AdminPrivilegeFilter;

import javax.servlet.ServletContext;

@Name("Workspace Import/Export")
@Description("Allows a user to import or export a workspace")
public class ImportExportWorkspaceWebAppPlugin implements WebAppPlugin {
    @Override
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {
        Class<? extends Handler> authenticationHandlerClass = authenticationHandler.getClass();
        Class<? extends Handler> csrfHandlerClass = VisalloCsrfHandler.class;

        app.registerJavaScriptTemplate("/org/visallo/web/importExportWorkspaces/import.hbs");
        app.registerJavaScriptTemplate("/org/visallo/web/importExportWorkspaces/export.hbs");

        app.registerJavaScript("/org/visallo/web/importExportWorkspaces/plugin.js");
        app.registerJavaScript("/org/visallo/web/importExportWorkspaces/import-plugin.js", false);
        app.registerJavaScript("/org/visallo/web/importExportWorkspaces/export-plugin.js", false);

        app.registerResourceBundle("/org/visallo/web/importExportWorkspaces/messages.properties");

        app.get("/admin/workspace/export", authenticationHandlerClass, csrfHandlerClass, AdminPrivilegeFilter.class, Export.class);
        app.post("/admin/workspace/import", authenticationHandlerClass, csrfHandlerClass, AdminPrivilegeFilter.class, Import.class);
    }
}
