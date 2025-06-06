import classNames from "classnames";
import isBoolean from "lodash/isBoolean";
import { useCallback, useMemo } from "react";
import { useFormContext, useWatch } from "react-hook-form";

import { ListBox } from "components/ui/ListBox";

import { AdditionalPropertiesControl } from "./AdditionalPropertiesControl";
import { ControlGroup } from "./ControlGroup";
import styles from "./MultiOptionControl.module.scss";
import { ObjectControl } from "./ObjectControl";
import { SchemaFormControl } from "./SchemaFormControl";
import { BaseControlComponentProps, OverrideByPath, BaseControlProps } from "./types";
import { useToggleConfig } from "./useToggleConfig";
import { useSchemaForm } from "../SchemaForm";
import { AirbyteJsonSchema, resolveTopLevelRef } from "../utils";

export const MultiOptionControl = ({
  fieldSchema,
  baseProps,
  overrideByPath = {},
  skipRenderedPathRegistration = false,
  hideBorder = false,
  nonAdvancedFields,
}: BaseControlComponentProps) => {
  const value: unknown = useWatch({ name: baseProps.name });
  const { setValue, unregister } = useFormContext();
  const {
    schema: rootSchema,
    getSelectedOptionSchema,
    errorAtPath,
    extractDefaultValuesFromSchema,
    verifyArrayItems,
  } = useSchemaForm();
  const toggleConfig = useToggleConfig(baseProps.name, fieldSchema);
  const error = errorAtPath(baseProps.name);
  const optionSchemas = fieldSchema.oneOf ?? fieldSchema.anyOf;
  const options = useMemo(
    () =>
      optionSchemas
        ?.map((optionSchema) => resolveTopLevelRef(rootSchema, optionSchema as AirbyteJsonSchema))
        ?.filter((optionSchema) => !isBoolean(optionSchema) && !optionSchema.deprecated) as AirbyteJsonSchema[],
    [optionSchemas, rootSchema]
  );
  const currentlySelectedOption = useMemo(
    () => (options ? getSelectedOptionSchema(options, value) : undefined),
    [getSelectedOptionSchema, options, value]
  );
  const displayError = useMemo(
    () => (currentlySelectedOption?.type === "object" ? error : undefined),
    [currentlySelectedOption, error]
  );

  const getOptionLabel = useCallback(
    (option: AirbyteJsonSchema | undefined): string => {
      if (option === undefined) {
        return "";
      }
      if (option.title) {
        return option.title;
      }
      if (option.type === undefined) {
        if (option.anyOf) {
          return "anyOf";
        }
        if (option.oneOf) {
          return "oneOf";
        }
        return "";
      }
      if (option.type === "array") {
        const items = verifyArrayItems(option.items);
        return `${option.type} of ${getOptionLabel(items)}`;
      }
      if (Array.isArray(option.type)) {
        return option.type.join(", ");
      }
      return option.type as string;
    },
    [verifyArrayItems]
  );

  if (options.length === 1) {
    return (
      <ObjectControl
        baseProps={baseProps}
        overrideByPath={overrideByPath}
        skipRenderedPathRegistration={skipRenderedPathRegistration}
        fieldSchema={options[0]}
        nonAdvancedFields={nonAdvancedFields}
      />
    );
  }

  if (!optionSchemas) {
    return null;
  }

  if (hideBorder) {
    return (
      <>
        {renderOptionContents(
          baseProps,
          currentlySelectedOption,
          overrideByPath,
          skipRenderedPathRegistration,
          nonAdvancedFields
        )}
      </>
    );
  }

  return (
    <ControlGroup
      title={baseProps.label}
      tooltip={baseProps.labelTooltip}
      path={baseProps.name}
      error={displayError}
      header={baseProps.header}
      control={
        <ListBox
          className={classNames({ [styles.listBoxError]: !!displayError })}
          options={options.map((option) => ({
            label: getOptionLabel(option),
            value: getOptionLabel(option),
          }))}
          onSelect={(selectedValue) => {
            const selectedOption = options.find((option) => selectedValue === getOptionLabel(option));
            if (!selectedOption) {
              setValue(baseProps.name, undefined);
              return;
            }

            // unregister the field to remove the validation logic of the previous option
            unregister(baseProps.name);

            const defaultValues = extractDefaultValuesFromSchema(selectedOption);
            setValue(baseProps.name, defaultValues, { shouldValidate: false });
          }}
          selectedValue={getOptionLabel(currentlySelectedOption)}
          adaptiveWidth={false}
        />
      }
      toggleConfig={baseProps.optional ? toggleConfig : undefined}
    >
      {renderOptionContents(
        baseProps,
        currentlySelectedOption,
        overrideByPath,
        skipRenderedPathRegistration,
        nonAdvancedFields
      )}
    </ControlGroup>
  );
};

// Render the selected option's properties
const renderOptionContents = (
  baseProps: BaseControlProps,
  selectedOption?: AirbyteJsonSchema,
  overrideByPath?: OverrideByPath,
  skipRenderedPathRegistration?: boolean,
  nonAdvancedFields?: string[]
) => {
  if (!selectedOption) {
    return null;
  }

  if (selectedOption.properties) {
    return (
      <ObjectControl
        baseProps={baseProps}
        overrideByPath={overrideByPath}
        skipRenderedPathRegistration={skipRenderedPathRegistration}
        hideBorder
        fieldSchema={selectedOption}
        nonAdvancedFields={nonAdvancedFields}
      />
    );
  }

  if (selectedOption.additionalProperties && !isBoolean(selectedOption.additionalProperties)) {
    return (
      <AdditionalPropertiesControl
        baseProps={baseProps}
        fieldSchema={selectedOption.additionalProperties}
        overrideByPath={overrideByPath}
        skipRenderedPathRegistration={skipRenderedPathRegistration}
        hideBorder
      />
    );
  }

  return (
    <SchemaFormControl
      key={baseProps.name}
      path={baseProps.name}
      overrideByPath={overrideByPath}
      skipRenderedPathRegistration={skipRenderedPathRegistration}
      fieldSchema={{
        ...selectedOption,
        // If the selectedOption has no title, then don't render any title for this field
        // since the parent parent MultiOptionControl is already rendering the title
        title: selectedOption.title ?? "",
      }}
      isRequired
    />
  );
};
