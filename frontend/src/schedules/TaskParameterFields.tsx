import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Controller, type Control } from 'react-hook-form';
import type { TaskDefinition, TaskParameter } from '../api/types';
import type { ScheduleFormValues } from './formModel';

function validateParameter(parameter: TaskParameter, value: string | undefined): true | string {
  const text = (value ?? '').trim();
  if (text === '') {
    return parameter.required ? `${parameter.label} is required` : true;
  }
  if (parameter.type === 'NUMBER' && !Number.isFinite(Number(text))) {
    return `${parameter.label} must be a number`;
  }
  return true;
}

interface TaskParameterFieldsProps {
  control: Control<ScheduleFormValues>;
  task: TaskDefinition;
}

/** One input per parameter the selected task declares, built from the task's parameter list. */
export function TaskParameterFields({ control, task }: TaskParameterFieldsProps) {
  if (task.parameters.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        This task has no inputs.
      </Typography>
    );
  }

  return (
    <Stack spacing={2}>
      {task.parameters.map((parameter) => (
        <Controller
          // The key includes the task, so switching task always starts from fresh fields.
          key={`${task.key}.${parameter.name}`}
          name={`parameters.${parameter.name}`}
          control={control}
          defaultValue=""
          shouldUnregister
          rules={{ validate: (value) => validateParameter(parameter, value) }}
          render={({ field: { ref, ...field }, fieldState }) => (
            <TextField
              {...field}
              inputRef={ref}
              label={parameter.label}
              type={parameter.type === 'NUMBER' ? 'number' : 'text'}
              required={parameter.required}
              fullWidth
              error={fieldState.invalid}
              helperText={fieldState.error?.message ?? parameter.description}
              slotProps={parameter.type === 'NUMBER' ? { htmlInput: { step: 'any' } } : undefined}
            />
          )}
        />
      ))}
    </Stack>
  );
}
