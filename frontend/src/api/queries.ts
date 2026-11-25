import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { createSchedule, deleteSchedule, listSchedules, listTasks, updateSchedule } from './client';
import type { ScheduleRequest } from './types';

/** The task list is fixed on the server, so it never goes stale while the page is open. */
export const tasksQuery = queryOptions({
  queryKey: ['tasks'],
  queryFn: listTasks,
  staleTime: Infinity,
});

export const schedulesQuery = queryOptions({
  queryKey: ['schedules'],
  queryFn: listSchedules,
});

interface SaveScheduleInput {
  /** Present when editing an existing schedule, absent when creating one. */
  id?: string;
  body: ScheduleRequest;
}

export function useSaveSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: SaveScheduleInput) =>
      id === undefined ? createSchedule(body) : updateSchedule(id, body),
    // Returning the promise keeps the mutation pending until the table has fresh data.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: schedulesQuery.queryKey }),
  });
}

export function useDeleteSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteSchedule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: schedulesQuery.queryKey }),
  });
}
