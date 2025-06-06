import { useFormContext } from "react-hook-form";
import { FormattedMessage } from "react-intl";

import { Button } from "components/ui/Button";
import { FlexContainer, FlexItem } from "components/ui/Flex";
import { Text } from "components/ui/Text";
import { InfoTooltip, Tooltip } from "components/ui/Tooltip";

import styles from "./BuilderInputPlaceholder.module.scss";
import { getLabelAndTooltip } from "./manifestHelpers";

export interface BuilderFieldProps {
  label?: string;
  tooltip?: string;
  manifestPath?: string;
}

export const BuilderInputPlaceholder = (props: BuilderFieldProps) => {
  const { setValue } = useFormContext();
  const { label, tooltip } = getLabelAndTooltip(props.label, props.tooltip, props.manifestPath, true);
  return (
    <FlexContainer alignItems="center">
      <FlexItem grow>
        <FlexContainer gap="none">
          <Text size="lg">{label}</Text>
          {tooltip && <InfoTooltip placement="top-start">{tooltip}</InfoTooltip>}
        </FlexContainer>
      </FlexItem>
      <Tooltip
        control={
          <Button
            icon="user"
            variant="link"
            onClick={() => setValue("view", { type: "inputs" })}
            className={styles.tooltipTrigger}
            iconClassName={styles.tooltipIcon}
          />
        }
      >
        <FormattedMessage id="connectorBuilder.placeholder.label" />
        <br />
        <Button
          variant="link"
          type="button"
          onClick={() => {
            setValue("view", { type: "inputs" });
          }}
        >
          <FormattedMessage id="connectorBuilder.placeholder.button" />
        </Button>
      </Tooltip>
    </FlexContainer>
  );
};
