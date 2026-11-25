import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import type { Schedule, TaskDefinition } from '../api/types';
import { describeTiming } from './describeTiming';

interface SchedulesTableProps {
  schedules: Schedule[];
  /** Used to show task and input names. The table still works with keys only if it is missing. */
  tasks: TaskDefinition[] | undefined;
  onEdit: (schedule: Schedule) => void;
  onDelete: (schedule: Schedule) => void;
}

export function SchedulesTable({ schedules, tasks, onEdit, onDelete }: SchedulesTableProps) {
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table aria-label="Schedules">
        <TableHead>
          <TableRow>
            <TableCell>Task</TableCell>
            <TableCell>When</TableCell>
            <TableCell>Inputs</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {schedules.map((schedule) => {
            const task = tasks?.find((candidate) => candidate.key === schedule.taskKey);
            const taskName = task?.name ?? schedule.taskKey;
            const inputs = Object.entries(schedule.parameters);

            return (
              <TableRow key={schedule.id} hover>
                <TableCell component="th" scope="row">
                  {taskName}
                </TableCell>
                <TableCell>{describeTiming(schedule.timing)}</TableCell>
                <TableCell sx={{ maxWidth: 360 }}>
                  {inputs.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      No inputs
                    </Typography>
                  ) : (
                    inputs.map(([name, value]) => {
                      const label =
                        task?.parameters.find((parameter) => parameter.name === name)?.label ??
                        name;
                      return (
                        <Typography key={name} variant="body2" noWrap title={`${label}: ${value}`}>
                          <Typography component="span" variant="body2" color="text.secondary">
                            {label}:
                          </Typography>{' '}
                          {String(value)}
                        </Typography>
                      );
                    })
                  )}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <Tooltip title="Edit">
                    <IconButton aria-label="Edit schedule" onClick={() => onEdit(schedule)}>
                      <EditOutlinedIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton aria-label="Delete schedule" onClick={() => onDelete(schedule)}>
                      <DeleteOutlineIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
