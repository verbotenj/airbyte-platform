
{{/* DO NOT EDIT: This file was autogenerated. */}}

{{/*
    Logging Configuration
*/}}

{{/*
Renders the logging.level value
*/}}
{{- define "airbyte-data-plane.logging.level" }}
    {{- .Values.logging.level | default "INFO" }}
{{- end }}

{{/*
Renders the logging.level environment variable
*/}}
{{- define "airbyte-data-plane.logging.level.env" }}
- name: LOG_LEVEL
  valueFrom:
    configMapKeyRef:
      name: {{ .Release.Name }}-airbyte-data-plane-env
      key: LOG_LEVEL
{{- end }}

{{/*
Renders the set of all logging environment variables
*/}}
{{- define "airbyte-data-plane.logging.envs" }}
{{- include "airbyte-data-plane.logging.level.env" . }}
{{- end }}

{{/*
Renders the set of all logging config map variables
*/}}
{{- define "airbyte-data-plane.logging.configVars" }}
LOG_LEVEL: {{ include "airbyte-data-plane.logging.level" . | quote }}
{{- end }}
