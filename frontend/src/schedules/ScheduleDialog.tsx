import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useQuery } from '@tanstack/react-query';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { ApiError } from '../api/client';
import { tasksQuery, useSaveSchedule } from '../api/queries';
import type { Schedule } from '../api/types';
import {
  browserTimeZone,
  type ScheduleFormValues,
  toFormField,
  toFormValues,
  toRequest,
} from './formModel';
import { TaskParameterFields } from './TaskParameterFields';
import { TimingFields } from './TimingFields';

const FORM_ID = 'schedule-form';
const TITLE_ID = 'schedule-dialog-title';
const GENERAL_ERROR = 'Could not save the schedule. Please try again.';

interface ScheduleDialogProps {
  /** The schedule to edit. Leave out to create a new one. */
  schedule?: Schedule;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * The add / edit form. Mount it only while it should be open: each mount starts from the
 * given schedule (or from an empty form).
 */
export function ScheduleDialog({ schedule, onClose, onSaved }: ScheduleDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const tasks = useQuery(tasksQuery);
  const saveSchedule = useSaveSchedule();

  const {
    control,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<ScheduleFormValues>({
    defaultValues: toFormValues(schedule),
  });

  // Editing keeps the zone the schedule was created in. Otherwise changing only a message from
  // another time zone would silently move "08:30" to a different moment.
  const timeZone = schedule?.timeZone ?? browserTimeZone();

  const taskKey = useWatch({ control, name: 'taskKey' });
  const selectedTask = tasks.data?.find((task) => task.key === taskKey);

  const onSubmit = handleSubmit(async (values) => {
    const task = tasks.data?.find((candidate) => candidate.key === values.taskKey);
    if (!task) {
      setError('taskKey', { type: 'validate', message: 'Pick a task' });
      return;
    }
    clearErrors('root.server');

    try {
      await saveSchedule.mutateAsync({ id: schedule?.id, body: toRequest(values, task, timeZone) });
    } catch (error) {
      const unmapped: string[] = [];
      if (error instanceof ApiError && error.status === 400) {
        for (const fieldError of error.fieldErrors) {
          const field = toFormField(fieldError.field, values.timingType, task);
          if (field) {
            setError(field, { type: 'server', message: fieldError.message });
          } else {
            unmapped.push(fieldError.message);
          }
        }
        if (error.fieldErrors.length > 0 && unmapped.length === 0) {
          return;
        }
      }
      const message =
        unmapped.length > 0
          ? unmapped.join(' ')
          : error instanceof ApiError
            ? (error.problem?.detail ?? GENERAL_ERROR)
            : 'Could not reach the server. Please try again.';
      setError('root.server', { type: 'server', message });
      return;
    }

    onSaved();
  });

  const close = () => {
    if (!isSubmitting) {
      onClose();
    }
  };

  return (
    <Dialog
      open
      onClose={close}
      fullWidth
      maxWidth="sm"
      fullScreen={fullScreen}
      aria-labelledby={TITLE_ID}
    >
      <DialogTitle id={TITLE_ID}>{schedule ? 'Edit schedule' : 'Add schedule'}</DialogTitle>

      <DialogContent dividers>
        {tasks.isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress aria-label="Loading tasks" />
          </Box>
        )}

        {tasks.isError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={() => void tasks.refetch()}>
                Retry
              </Button>
            }
          >
            Could not load the task list.
          </Alert>
        )}

        {tasks.isSuccess && (
          <form
            id={FORM_ID}
            noValidate
            onSubmit={(event) => {
              if (isSubmitting) {
                // Already saving: ignore Enter pressed in a field.
                event.preventDefault();
                return;
              }
              void onSubmit(event);
            }}
          >
            <Stack spacing={3}>
              {errors.root?.server?.message && (
                <Alert severity="error">{errors.root.server.message}</Alert>
              )}

              <Controller
                name="taskKey"
                control={control}
                rules={{ required: 'Pick a task' }}
                render={({ field: { ref, ...field }, fieldState }) => (
                  <TextField
                    {...field}
                    inputRef={ref}
                    select
                    label="Task"
                    required
                    fullWidth
                    error={fieldState.invalid}
                    helperText={fieldState.error?.message ?? selectedTask?.description}
                  >
                    {tasks.data.map((task) => (
                      <MenuItem key={task.key} value={task.key}>
                        {task.name}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />

              <TimingFields control={control} timeZone={timeZone} />

              {selectedTask && <TaskParameterFields control={control} task={selectedTask} />}
            </Stack>
          </form>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={close} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button
          type="submit"
          form={FORM_ID}
          variant="contained"
          disabled={!tasks.isSuccess || isSubmitting}
        >
          {isSubmitting ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
