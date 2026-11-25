import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useQuery } from '@tanstack/react-query';
import { executionsQuery } from '../api/queries';
import type { Schedule } from '../api/types';
import { formatDuration, formatMoment } from './describeExecution';
import { describeTiming } from './describeTiming';
import { RunStatusChip } from './RunStatusChip';

const TITLE_ID = 'run-history-title';
const TEXT_ID = 'run-history-text';

interface RunHistoryDialogProps {
  schedule: Schedule;
  taskName: string;
  /** Turns a task key into the task's name, for runs made before the schedule switched task. */
  taskNameOf: (taskKey: string) => string;
  onClose: () => void;
}

/**
 * The past runs of one schedule, newest first. It keeps itself up to date while open, so a run
 * that happens meanwhile appears without reopening. Mount it only while it should be open.
 */
export function RunHistoryDialog({
  schedule,
  taskName,
  taskNameOf,
  onClose,
}: RunHistoryDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const runs = useQuery(executionsQuery(schedule.id));

  return (
    <Dialog
      open
      onClose={onClose}
      fullScreen={fullScreen}
      fullWidth
      maxWidth="md"
      aria-labelledby={TITLE_ID}
      aria-describedby={TEXT_ID}
    >
      <DialogTitle id={TITLE_ID}>Run history</DialogTitle>
      <DialogContent dividers>
        <DialogContentText id={TEXT_ID} sx={{ mb: 2 }}>
          {taskName}, {describeTiming(schedule.timing)}
        </DialogContentText>

        {runs.isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress aria-label="Loading run history" />
          </Box>
        )}

        {runs.isError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={() => void runs.refetch()}>
                Retry
              </Button>
            }
          >
            Could not load the run history.
          </Alert>
        )}

        {runs.isSuccess && runs.data.length === 0 && (
          <Typography color="text.secondary" sx={{ py: 2 }}>
            This schedule has not run yet.
          </Typography>
        )}

        {runs.isSuccess && runs.data.length > 0 && (
          <>
            <TableContainer>
              <Table size="small" aria-label="Runs">
                <TableHead>
                  <TableRow>
                    <TableCell>Started</TableCell>
                    <TableCell>Took</TableCell>
                    <TableCell>Task</TableCell>
                    <TableCell>Result</TableCell>
                    <TableCell>Error</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {runs.data.map((run) => (
                    <TableRow key={run.id}>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {formatMoment(run.startedAt)}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {formatDuration(run.startedAt, run.finishedAt)}
                      </TableCell>
                      <TableCell>{taskNameOf(run.taskKey)}</TableCell>
                      <TableCell>
                        <RunStatusChip status={run.status} />
                      </TableCell>
                      <TableCell sx={{ wordBreak: 'break-word' }}>
                        {run.errorMessage ?? ''}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
              Newest first. Only the newest 100 runs of a schedule are kept.
            </Typography>
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
