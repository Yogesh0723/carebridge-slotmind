export type Status =
  | "CONFIRMED"
  | "RESCHEDULE_PENDING"
  | "RESCHEDULED"
  | "CANCELLED"
  | "COMPLETED";
export interface Appointment {
  id: string;
  patientName: string;
  type: string;
  status: Status;
  startTime: string;
  providerId: string;
  providerName: string;
  slotId: string;
}
export interface Provider { id: string; name: string; specialty: string; status: string; maxDailyAppointments: number; }
export interface Slot { id: string; providerName: string; providerId?: string; providerStatus?: string; startTime: string; durationMinutes: number; status: string; room?: string; equipment?: string; }
export interface Suggestion {
  id: string;
  appointmentId: string;
  slotId: string;
  recommendationRank: number;
  status: string;
  reason: string;
  confidence: number;
  explanation: string;
  patientScript: string;
  startTime: string;
  providerName: string;
}
