import classNames from "classnames";
import { PropsWithChildren, useMemo } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { matchPath, useLocation } from "react-router-dom";

import { AdminWorkspaceWarning } from "components/ui/AdminWorkspaceWarning";
import { Box } from "components/ui/Box";
import { FlexContainer } from "components/ui/Flex";
import { Icon } from "components/ui/Icon";
import { ThemeToggle } from "components/ui/ThemeToggle";
import { WorkspacesPicker } from "components/workspace/WorkspacesPicker";

import { AirbyteOrgPicker } from "area/sidebar/components/AirbyteOrgPicker";
import { useAuthService } from "core/services/auth";
import { FeatureItem, IfFeatureEnabled } from "core/services/features";
import { useExperiment } from "hooks/services/Experiment";
import { ConnectorBuilderRoutePaths } from "pages/connectorBuilder/ConnectorBuilderRoutes";
import { RoutePaths } from "pages/routePaths";

import { AirbyteHomeLink } from "./AirbyteHomeLink";
import { MenuContent } from "./components/MenuContent";
import { NavDropdown } from "./components/NavDropdown";
import { NavItem } from "./components/NavItem";
import styles from "./SideBar.module.scss";

interface SideBarProps {
  bottomSlot?: React.ReactNode;
  settingHighlight?: boolean;
}

const HIDDEN_SIDEBAR_PATHS = [
  `${RoutePaths.Workspaces}/:workspaceId/${RoutePaths.ConnectorBuilder}/${ConnectorBuilderRoutePaths.Edit}`,
];

export const SideBar: React.FC<PropsWithChildren<SideBarProps>> = ({ bottomSlot, settingHighlight }) => {
  const { logout, user, authType } = useAuthService();
  const { formatMessage } = useIntl();

  const { pathname } = useLocation();
  const isHidden = HIDDEN_SIDEBAR_PATHS.some((path) => !!matchPath(path, pathname));

  const username = useMemo(() => {
    if (authType === "simple" || authType === "none") {
      return formatMessage({ id: "sidebar.defaultUsername" });
    }
    return user?.name?.trim() || user?.email?.trim();
  }, [authType, user?.name, user?.email, formatMessage]);

  const isOrgPickerEnabled = useExperiment("sidebar.showOrgPicker");

  return (
    <nav className={classNames(styles.sidebar, { [styles.hidden]: isHidden })}>
      {isOrgPickerEnabled ? (
        <>
          <AirbyteOrgPicker />
          <IfFeatureEnabled feature={FeatureItem.ShowAdminWarningInWorkspace}>
            <AdminWorkspaceWarning />
          </IfFeatureEnabled>
          <FlexContainer className={styles.sidebar__menuItems} direction="column" justifyContent="flex-start">
            <MenuContent data-testid="navMainItems">
              <NavItem
                label={<FormattedMessage id="sidebar.connections" />}
                icon="connection"
                to={RoutePaths.Connections}
                testId="connectionsLink"
              />
              <NavItem
                label={<FormattedMessage id="sidebar.sources" />}
                icon="source"
                to={RoutePaths.Source}
                testId="sourcesLink"
              />
              <NavItem
                label={<FormattedMessage id="sidebar.destinations" />}
                icon="destination"
                testId="destinationsLink"
                to={RoutePaths.Destination}
              />
              <NavItem
                label={<FormattedMessage id="sidebar.builder" />}
                icon="wrench"
                testId="builderLink"
                to={RoutePaths.ConnectorBuilder}
              />
              <NavItem
                label={<FormattedMessage id="sidebar.settings" />}
                icon="gear"
                to={RoutePaths.Settings}
                withNotification={settingHighlight}
              />
            </MenuContent>
            <Box className={styles.sidebar__menuContentSeparator} />
            <MenuContent>
              {bottomSlot}
              <ThemeToggle />
              {logout && user && (
                <NavDropdown
                  buttonTestId="sidebar.userDropdown"
                  onChange={({ value }) => {
                    value === "logout" && logout();
                  }}
                  options={[
                    {
                      as: "a",
                      href: RoutePaths.Settings,
                      displayName: formatMessage({ id: "sidebar.userSettings" }),
                      internal: true,
                      icon: <Icon type="gear" />,
                    },
                    {
                      as: "button",
                      displayName: formatMessage({ id: "sidebar.logout" }),
                      icon: <Icon type="signout" />,
                      value: "logout",
                      className: styles.sidebar__logoutButton,
                      "data-testid": "sidebar.signout",
                    },
                  ]}
                  icon="user"
                  label={username}
                />
              )}
            </MenuContent>
          </FlexContainer>
        </>
      ) : (
        <>
          <AirbyteHomeLink />
          <IfFeatureEnabled feature={FeatureItem.ShowAdminWarningInWorkspace}>
            <AdminWorkspaceWarning />
          </IfFeatureEnabled>
          <IfFeatureEnabled feature={FeatureItem.MultiWorkspaceUI}>
            <WorkspacesPicker />
          </IfFeatureEnabled>
          <FlexContainer className={styles.sidebar__menuItems} direction="column" justifyContent="space-between">
            <MenuContent data-testid="navMainItems">
              <NavItem
                label={<FormattedMessage id="sidebar.connections" />}
                icon="connection"
                to={RoutePaths.Connections}
                testId="connectionsLink"
              />
              <NavItem
                label={<FormattedMessage id="sidebar.sources" />}
                icon="source"
                to={RoutePaths.Source}
                testId="sourcesLink"
              />
              <NavItem
                label={<FormattedMessage id="sidebar.destinations" />}
                icon="destination"
                testId="destinationsLink"
                to={RoutePaths.Destination}
              />
              <NavItem
                label={<FormattedMessage id="sidebar.builder" />}
                icon="wrench"
                testId="builderLink"
                to={RoutePaths.ConnectorBuilder}
              />
              <NavItem
                label={<FormattedMessage id="sidebar.settings" />}
                icon="gear"
                to={RoutePaths.Settings}
                withNotification={settingHighlight}
              />
            </MenuContent>
            <MenuContent>
              {bottomSlot}
              <ThemeToggle />
              {logout && user && (
                <NavDropdown
                  buttonTestId="sidebar.userDropdown"
                  onChange={({ value }) => {
                    value === "logout" && logout();
                  }}
                  options={[
                    {
                      as: "a",
                      href: RoutePaths.Settings,
                      displayName: formatMessage({ id: "sidebar.userSettings" }),
                      internal: true,
                      icon: <Icon type="gear" />,
                    },
                    {
                      as: "button",
                      displayName: formatMessage({ id: "sidebar.logout" }),
                      icon: <Icon type="signout" />,
                      value: "logout",
                      className: styles.sidebar__logoutButton,
                      "data-testid": "sidebar.signout",
                    },
                  ]}
                  icon="user"
                  label={username}
                />
              )}
            </MenuContent>
          </FlexContainer>
        </>
      )}
    </nav>
  );
};
