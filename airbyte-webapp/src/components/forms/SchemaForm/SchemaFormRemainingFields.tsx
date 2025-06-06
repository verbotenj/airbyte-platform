import { ReactElement } from "react";
import { useWatch } from "react-hook-form";

import { SchemaFormControl } from "./Controls/SchemaFormControl";
import { useSchemaForm } from "./SchemaForm";

type OverrideByPath = Record<string, ReactElement | null>;

/**
 * Component that renders form controls that haven't been rendered by other SchemaFormControl components
 */
export interface SchemaFormRemainingFieldsProps {
  /**
   * Path to the property in the schema. Empty string or undefined for root level properties.
   */
  path?: string;

  /**
   * Map of property paths to custom renderers, allowing override of specific fields.
   */
  overrideByPath?: OverrideByPath;
}

export const SchemaFormRemainingFields = ({ path = "", overrideByPath = {} }: SchemaFormRemainingFieldsProps) => {
  const { getSchemaAtPath, isPathRendered } = useSchemaForm();
  const value = useWatch({ name: path });

  // Get the property at the specified path
  const targetProperty = getSchemaAtPath(path, value);

  // If not an object or has no properties, nothing to render
  if (targetProperty.type !== "object" || !targetProperty.properties) {
    return null;
  }

  // Render only properties that haven't been rendered yet
  return (
    <>
      {Object.keys(targetProperty.properties).map((propertyName) => {
        const fullPath = path ? `${path}.${propertyName}` : propertyName;

        // Skip if this path or any parent path has already been rendered
        if (isPathRendered(fullPath)) {
          return null;
        }

        // Use skipRenderedPathRegistration=true to prevent double registration
        return (
          <SchemaFormControl
            key={fullPath}
            path={fullPath}
            overrideByPath={overrideByPath}
            skipRenderedPathRegistration
            isRequired={targetProperty.required?.includes(propertyName) ?? false}
          />
        );
      })}
    </>
  );
};
