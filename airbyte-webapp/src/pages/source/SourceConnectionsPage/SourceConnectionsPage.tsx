import React, { useMemo } from "react";
import { createSearchParams, useNavigate } from "react-router-dom";

import { ConnectorEmptyStateContent } from "components/connector/ConnectorEmptyStateContent";
import { TableItemTitle } from "components/ConnectorBlocks";
import { ConnectorIcon } from "components/ConnectorIcon";
import { DropdownMenuOptionType } from "components/ui/DropdownMenu";
import { FlexContainer } from "components/ui/Flex/FlexContainer";

import { useGetSourceFromParams } from "area/connector/utils";
import { useCurrentWorkspace, useConnectionList, useDestinationList, useListConnectionsStatusesAsync } from "core/api";
import { WebBackendConnectionListItem } from "core/api/types/AirbyteClient";
import { useExperiment } from "hooks/services/Experiment";
import { ConnectionRoutePaths, RoutePaths } from "pages/routePaths";

import styles from "./SourceConnectionsPage.module.scss";

const SourceConnectionTable = React.lazy(() => import("./SourceConnectionTable"));

const emptyArray: never[] = [];
export const SourceConnectionsPage = () => {
  const source = useGetSourceFromParams();
  const { workspaceId } = useCurrentWorkspace();
  const connectionList = useConnectionList({ sourceId: [source.sourceId] });
  const connections = connectionList?.connections ?? (emptyArray as WebBackendConnectionListItem[]);

  const isAllConnectionsStatusEnabled = useExperiment("connections.connectionsStatusesEnabled");
  const connectionIds = useMemo(() => connections.map((connection) => connection.connectionId), [connections]);
  useListConnectionsStatusesAsync(connectionIds, isAllConnectionsStatusEnabled);

  // We load all destinations so the add destination button has a pre-filled list of options.
  const { destinations } = useDestinationList();

  const navigate = useNavigate();

  const destinationDropdownOptions: DropdownMenuOptionType[] = useMemo(
    () =>
      destinations.map((destination) => {
        return {
          as: "button",
          icon: <ConnectorIcon icon={destination.icon} />,
          iconPosition: "right",
          displayName: destination.name,
          value: destination.destinationId,
        };
      }),
    [destinations]
  );

  const onSelect = (data: DropdownMenuOptionType) => {
    const path = `/${RoutePaths.Workspaces}/${workspaceId}/${RoutePaths.Connections}/${ConnectionRoutePaths.ConnectionNew}`;

    const searchParams =
      data.value !== "create-new-item"
        ? createSearchParams({ destinationId: data.value as string, sourceId: source.sourceId })
        : createSearchParams({ sourceId: source.sourceId, destinationType: "new" });

    navigate({ pathname: path, search: `?${searchParams}` });
  };

  return (
    <>
      {connections.length ? (
        <FlexContainer direction="column" gap="xl" className={styles.fullHeight}>
          <TableItemTitle
            type="destination"
            dropdownOptions={destinationDropdownOptions}
            onSelect={onSelect}
            connectionsCount={connections ? connections.length : 0}
          />
          <SourceConnectionTable connections={connections} />
        </FlexContainer>
      ) : (
        <ConnectorEmptyStateContent
          connectorId={source.sourceId}
          icon={source.icon}
          connectorType="source"
          connectorName={source.name}
        />
      )}
    </>
  );
};
