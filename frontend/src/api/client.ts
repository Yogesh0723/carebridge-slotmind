import type { Appointment, Suggestion, Provider, Slot } from "../types";
const base = "http://localhost:8080/api";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(base + path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}
export const api = {
  appointments: () => request<Appointment[]>("/appointments"),
  providers: () => request<Provider[]>("/providers"),
  slots: () => request<Slot[]>("/slots"),
  createAppointment: (body: Record<string, unknown>) => request<Appointment>("/appointments", { method: "POST", body: JSON.stringify(body) }),
  cancelAppointment: (id: string) => request<Appointment>(`/appointments/${id}/cancel`, { method: "POST" }),
  completeAppointment: (id: string) => request<Appointment>(`/appointments/${id}/complete`, { method: "POST" }),
  suggest: (id: string) => request<Appointment>(`/appointments/${id}/suggest`, { method: "POST" }),
  providerStatus: (id: string, value: string) => request<Provider>(`/providers/${id}/status?value=${value}`, { method: "PATCH" }),
  slotStatus: (id: string, value: string) => request<Slot>(`/slots/${id}/status?value=${value}`, { method: "PATCH" }),
  suggestions: (id: string) =>
    request<Suggestion[]>(`/appointments/${id}/suggestions`),
  disrupt: (id: string) =>
    request<Appointment>(`/appointments/${id}/disrupt`, { method: "POST" }),
  accept: (id: string) =>
    request<Suggestion>(`/suggestions/${id}/accept`, { method: "POST" }),
  reject: (id: string) =>
    request<Suggestion>(`/suggestions/${id}/reject`, { method: "POST" }),
};
