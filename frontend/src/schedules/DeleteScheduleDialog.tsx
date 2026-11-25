import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import { useDeleteSchedule } from '../api/queries';
import type { Schedule } from '../api/types';
import { describeTiming } from './describeTiming';

const TITLE_ID = 'delete-schedule-title';
const TEXT_ID = 'delete-schedule-text';

interface DeleteScheduleDialogProps {
  schedule: Schedule;
  taskName: string;
  onClose: () => void;
  onDeleted: () => void;
}

/** Asks before deleting. Mount it only while it should be open. */
export function DeleteScheduleDialog({
  schedule,
  taskName,
  onClose,
  onDeleted,
}: DeleteScheduleDialogProps) {
  const deleteSchedule = useDeleteSchedule();
  const deleting = deleteSchedule.isPending;

  const confirm = () => {
    deleteSchedule.mutate(schedule.id, { onSuccess: onDeleted });
  };

  const close = () => {
    if (!deleting) {
      onClose();
    }
  };

  return (
    <Dialog open onClose={close} aria-labelledby={TITLE_ID} aria-describedby={TEXT_ID}>
      <DialogTitle id={TITLE_ID}>Delete this schedule?</DialogTitle>
      <DialogContent>
        <DialogContentText id={TEXT_ID}>
          {taskName}, {describeTiming(schedule.timing)}. It will stop running and cannot be brought
          back.
        </DialogContentText>
        {deleteSchedule.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            Could not delete the schedule. Please try again.
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={close} disabled={deleting}>
          Cancel
        </Button>
        <Button onClick={confirm} color="error" variant="contained" disabled={deleting}>
          {deleting ? 'Deleting…' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
