import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormGroup from '@mui/material/FormGroup';
import FormHelperText from '@mui/material/FormHelperText';
import FormLabel from '@mui/material/FormLabel';
import MenuItem from '@mui/material/MenuItem';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import type { ReactNode } from 'react';
import { Controller, useWatch, type Control, type FieldError } from 'react-hook-form';
import { DAYS_OF_WEEK, type DayOfWeek, type TimingType } from '../api/types';
import { DAY_FULL_NAMES, DAY_SHORT_NAMES } from './describeTiming';
import type { ScheduleFormValues } from './formModel';

const TIMING_KINDS: { value: TimingType; label: string }[] = [
  { value: 'ONE_TIME', label: 'One time' },
  { value: 'INTERVAL', label: 'Every X' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'CRON', label: 'Cron' },
];

const CRON_HELP =
  'Quartz format: 6 or 7 parts, starting with seconds (not the 5-part Unix format). ' +
  'Example: 0 0/5 * * * ? runs every 5 minutes.';

/**
 * The format help is always shown, except under an error from the backend: that message
 * already explains what is wrong with the expression, so repeating the help would be noise.
 */
function cronHelperText(error: FieldError | undefined): ReactNode {
  if (!error?.message) {
    return CRON_HELP;
  }
  if (error.type === 'server') {
    return error.message;
  }
  return (
    <>
      {error.message}
      <br />
      {CRON_HELP}
    </>
  );
}

const WHOLE_NUMBER = /^\d+$/;

function validateEvery(value: string): true | string {
  if (!WHOLE_NUMBER.test(value.trim()) || Number(value) < 1) {
    return 'Enter a whole number, 1 or more';
  }
  return true;
}

function validateFutureDate(value: string): true | string {
  const time = new Date(value).getTime();
  if (Number.isNaN(time)) {
    return 'Pick a date and time';
  }
  return time > Date.now() || 'Pick a date and time in the future';
}

interface TimingFieldsProps {
  control: Control<ScheduleFormValues>;
}

/**
 * The timing kind selector plus the fields of the selected kind. Only those fields are mounted,
 * and a field that leaves the screen also leaves the form (shouldUnregister), so it is neither
 * checked nor sent.
 */
export function TimingFields({ control }: TimingFieldsProps) {
  const timingType = useWatch({ control, name: 'timingType' });

  return (
    <Stack spacing={2}>
      <Controller
        name="timingType"
        control={control}
        render={({ field, fieldState }) => (
          <FormControl error={fieldState.invalid}>
            <FormLabel id="timing-type-label">When to run</FormLabel>
            <RadioGroup
              row
              aria-labelledby="timing-type-label"
              name={field.name}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
            >
              {TIMING_KINDS.map((kind) => (
                <FormControlLabel
                  key={kind.value}
                  value={kind.value}
                  control={<Radio />}
                  label={kind.label}
                />
              ))}
            </RadioGroup>
            {fieldState.error?.message && (
              <FormHelperText>{fieldState.error.message}</FormHelperText>
            )}
          </FormControl>
        )}
      />

      {timingType === 'ONE_TIME' && (
        <Controller
          name="runAt"
          control={control}
          shouldUnregister
          rules={{ required: 'Pick a date and time', validate: validateFutureDate }}
          render={({ field: { ref, ...field }, fieldState }) => (
            <TextField
              {...field}
              inputRef={ref}
              label="Date and time"
              type="datetime-local"
              required
              fullWidth
              error={fieldState.invalid}
              helperText={fieldState.error?.message ?? 'In your local time'}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          )}
        />
      )}

      {timingType === 'INTERVAL' && (
        <Stack direction="row" spacing={2}>
          <Controller
            name="every"
            control={control}
            shouldUnregister
            rules={{ required: 'Enter a whole number, 1 or more', validate: validateEvery }}
            render={({ field: { ref, ...field }, fieldState }) => (
              <TextField
                {...field}
                inputRef={ref}
                label="Every"
                type="number"
                required
                fullWidth
                error={fieldState.invalid}
                helperText={fieldState.error?.message}
                slotProps={{ htmlInput: { min: 1, step: 1 } }}
              />
            )}
          />
          <Controller
            name="unit"
            control={control}
            shouldUnregister
            render={({ field: { ref, ...field }, fieldState }) => (
              <TextField
                {...field}
                inputRef={ref}
                select
                label="Unit"
                fullWidth
                error={fieldState.invalid}
                helperText={fieldState.error?.message}
              >
                <MenuItem value="MINUTES">Minutes</MenuItem>
                <MenuItem value="HOURS">Hours</MenuItem>
              </TextField>
            )}
          />
        </Stack>
      )}

      {timingType === 'WEEKLY' && (
        <>
          <Controller
            name="days"
            control={control}
            shouldUnregister
            rules={{ validate: (days) => days.length > 0 || 'Pick at least one day' }}
            render={({ field, fieldState }) => {
              const toggle = (day: DayOfWeek, checked: boolean) => {
                const next = checked
                  ? [...field.value, day]
                  : field.value.filter((selected) => selected !== day);
                field.onChange(DAYS_OF_WEEK.filter((candidate) => next.includes(candidate)));
              };
              return (
                <FormControl component="fieldset" required error={fieldState.invalid}>
                  <FormLabel component="legend">Days</FormLabel>
                  <FormGroup row>
                    {DAYS_OF_WEEK.map((day, index) => (
                      <FormControlLabel
                        key={day}
                        label={DAY_SHORT_NAMES[day]}
                        control={
                          <Checkbox
                            checked={field.value.includes(day)}
                            onChange={(_event, checked) => toggle(day, checked)}
                            onBlur={field.onBlur}
                            slotProps={{
                              input: {
                                'aria-label': DAY_FULL_NAMES[day],
                                ref: index === 0 ? field.ref : undefined,
                              },
                            }}
                          />
                        }
                      />
                    ))}
                  </FormGroup>
                  {fieldState.error?.message && (
                    <FormHelperText>{fieldState.error.message}</FormHelperText>
                  )}
                </FormControl>
              );
            }}
          />
          <Controller
            name="time"
            control={control}
            shouldUnregister
            rules={{ required: 'Pick a time' }}
            render={({ field: { ref, ...field }, fieldState }) => (
              <TextField
                {...field}
                inputRef={ref}
                label="Time"
                type="time"
                required
                fullWidth
                error={fieldState.invalid}
                helperText={fieldState.error?.message ?? 'In your local time'}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            )}
          />
        </>
      )}

      {timingType === 'CRON' && (
        <Controller
          name="expression"
          control={control}
          shouldUnregister
          rules={{ validate: (value) => value.trim() !== '' || 'Enter a cron expression' }}
          render={({ field: { ref, ...field }, fieldState }) => (
            <TextField
              {...field}
              inputRef={ref}
              label="Cron expression"
              required
              fullWidth
              error={fieldState.invalid}
              helperText={cronHelperText(fieldState.error)}
              placeholder="0 0/5 * * * ?"
              slotProps={{
                htmlInput: { spellCheck: false, autoCapitalize: 'off', autoCorrect: 'off' },
              }}
            />
          )}
        />
      )}
    </Stack>
  );
}
