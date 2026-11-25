import Chip from '@mui/material/Chip';
import type { ExecutionStatus } from '../api/types';

const LABELS: Record<ExecutionStatus, string> = { SUCCEEDED: 'Succeeded', FAILED: 'Failed' };

/** How a run ended. The word carries the meaning; the colour only repeats it. */
export function RunStatusChip({ status }: { status: ExecutionStatus }) {
  return (
    <Chip
      size="small"
      variant="outlined"
      color={status === 'SUCCEEDED' ? 'success' : 'error'}
      label={LABELS[status]}
    />
  );
}
