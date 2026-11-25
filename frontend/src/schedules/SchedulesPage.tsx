import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import Paper from '@mui/material/Paper';
import Snackbar from '@mui/material/Snackbar';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { schedulesQuery, tasksQuery } from '../api/queries';
import type { Schedule } from '../api/types';
import { DeleteScheduleDialog } from './DeleteScheduleDialog';
import { ScheduleDialog } from './ScheduleDialog';
import { SchedulesTable } from './SchedulesTable';

type FormState = { open: false } | { open: true; schedule?: Schedule };

export function SchedulesPage() {
  const schedules = useQuery(schedulesQuery);
  const tasks = useQuery(tasksQuery);

  const [form, setForm] = useState<FormState>({ open: false });
  const [toDelete, setToDelete] = useState<Schedule | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const taskNameOf = (schedule: Schedule) =>
    tasks.data?.find((task) => task.key === schedule.taskKey)?.name ?? schedule.taskKey;

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack
        direction="row"
        spacing={2}
        sx={{ mb: 3, alignItems: 'center', justifyContent: 'space-between' }}
      >
        <Typography variant="h4" component="h1">
          Schedules
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setForm({ open: true })}>
          Add schedule
        </Button>
      </Stack>

      {schedules.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress aria-label="Loading schedules" />
        </Box>
      )}

      {schedules.isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => void schedules.refetch()}>
              Retry
            </Button>
          }
        >
          Could not load the schedules.
        </Alert>
      )}

      {schedules.isSuccess && schedules.data.length === 0 && (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center' }}>
          <Typography variant="h6" component="p" gutterBottom>
            No schedules yet
          </Typography>
          <Typography color="text.secondary">
            Use “Add schedule” to run a task once or on a repeating schedule.
          </Typography>
        </Paper>
      )}

      {schedules.isSuccess && schedules.data.length > 0 && (
        <SchedulesTable
          schedules={schedules.data}
          tasks={tasks.data}
          onEdit={(schedule) => setForm({ open: true, schedule })}
          onDelete={setToDelete}
        />
      )}

      {form.open && (
        <ScheduleDialog
          schedule={form.schedule}
          onClose={() => setForm({ open: false })}
          onSaved={() => {
            setForm({ open: false });
            setNotice('Schedule saved');
          }}
        />
      )}

      {toDelete && (
        <DeleteScheduleDialog
          schedule={toDelete}
          taskName={taskNameOf(toDelete)}
          onClose={() => setToDelete(null)}
          onDeleted={() => {
            setToDelete(null);
            setNotice('Schedule deleted');
          }}
        />
      )}

      <Snackbar
        open={notice !== null}
        autoHideDuration={4000}
        onClose={() => setNotice(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" variant="filled" onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      </Snackbar>
    </Container>
  );
}
