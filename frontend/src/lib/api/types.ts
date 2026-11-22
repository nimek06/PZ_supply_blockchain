import type { components } from './schema.gen'

// Krótkie nazwy typów z OpenAPI. Plik schema.gen.ts generuje `npm run gen:api`, nie edytuj go ręcznie.
type Schemas = components['schemas']

export type Problem = Schemas['Problem']
export type AuthChallenge = Schemas['AuthChallenge']
export type AuthSession = Schemas['AuthSession']
export type SessionContext = Schemas['SessionContext']
export type Role = Schemas['Role']
export type BatchResponse = Schemas['BatchResponse']
export type ProjectionFreshness = Schemas['ProjectionFreshness']
export type CommandAccepted = Schemas['CommandAccepted']
export type CommandStatus = Schemas['CommandStatus']
export type CommandState = Schemas['CommandState']
export type Outcome = Schemas['Outcome']
export type HistoryEntry = Schemas['HistoryEntry']
export type StageCode = Schemas['StageCode']
