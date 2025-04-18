import classNames from "classnames";
import React from "react";

import { Heading } from "components/ui/Heading";

import styles from "./BuilderConfigView.module.scss";

interface BuilderConfigViewProps {
  heading?: React.ReactNode;
  className?: string;
}

export const BuilderConfigView: React.FC<React.PropsWithChildren<BuilderConfigViewProps>> = ({
  children,
  heading,
  className,
}) => {
  return (
    <div className={classNames(styles.container, className)}>
      {heading && (
        <Heading className={styles.heading} as="h1">
          {heading}
        </Heading>
      )}
      {children}
    </div>
  );
};
