"""The public tool contract rejects extra fields at every nesting level."""

from typing import Annotated, Literal

from pydantic import MISSING, BaseModel, ConfigDict, Field, field_validator


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, allow_inf_nan=False, hide_input_in_errors=True)


class Empty(Strict):
    pass


class Page(Strict):
    limit: Annotated[int, Field(ge=1, le=50)] = 20
    cursor: Annotated[str, Field(min_length=1, max_length=128)] | MISSING = MISSING


UUID_PATTERN = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
UuidText = Annotated[str, Field(pattern=UUID_PATTERN)]


class WorkoutId(Strict):
    id: Annotated[str, Field(pattern="^(synced|imported):" + UUID_PATTERN[1:])]


class DraftId(Strict):
    id: UuidText


class Search(Strict):
    query: Annotated[str, Field(min_length=1, max_length=80)]
    limit: Annotated[int, Field(ge=1, le=20)] = 20


Number = int | float
Metric = Literal["REPS", "HOLD", "DURATION", "DISTANCE_TIME", "ATTEMPTS_GRADE"]


class Entry(Strict):
    exerciseKey: Annotated[str, Field(min_length=1, max_length=128)]
    sets: Annotated[int, Field(ge=1, le=50)]
    reps: Annotated[int, Field(ge=1, le=1000)]
    loadKg: Annotated[Number, Field(ge=0, le=1000)] | None


class DraftV1(Strict):
    schemaVersion: Annotated[int, Field(ge=1, le=1)]
    name: Annotated[str, Field(min_length=1, max_length=80)]
    warmup: Literal["AUTO", "NONE"]
    cooldown: Literal["AUTO", "NONE"]
    entries: Annotated[list[Entry], Field(min_length=1, max_length=20)]

    @field_validator("name")
    @classmethod
    def clean_name(cls, value):
        if value != value.strip() or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value):
            raise ValueError("invalid name")
        return value


class Create(Strict):
    idempotencyKey: Annotated[str, Field(pattern=r"^[A-Za-z0-9._~-]{1,80}$")]
    draft: DraftV1


class Envelope(Strict):
    id: UuidText
    revision: Annotated[int, Field(ge=1, le=1)]
    contentHash: Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]
    clientName: str
    createdAtMs: int
    expiresAtMs: int
    status: Literal["pending", "approved", "accepted", "expired", "rejected"]
    draft: DraftV1


class Success(Strict):
    schemaVersion: Annotated[int, Field(ge=1, le=1)]
    ok: Literal[True]


class DraftResult(Success):
    draft: Envelope


class Movement(Strict):
    key: str
    name: str
    metric: Metric


class Movements(Success):
    movements: Annotated[list[Movement], Field(max_length=20)]


class Summary(WorkoutId):
    label: str | None
    startedAtMs: int | None
    completedAtMs: int


class History(Success):
    workouts: Annotated[list[Summary], Field(max_length=50)]
    nextCursor: str | None


class LoggedSet(Strict):
    exerciseName: str | None
    setIndex: int | None
    reps: Number | None
    loadKg: Number | None
    durationSec: Number | None
    distanceM: Number | None
    done: bool | None


class Detail(Success):
    workout: Summary
    sets: Annotated[list[LoggedSet], Field(max_length=200)]
    truncated: bool


class Week(Strict):
    workouts: Number | None
    hardSets: Number | None
    volumeKg: Number | None
    prevVolumeKg: Number | None
    startDay: str | None
    endDay: str | None


class Peak(Strict):
    reps: Number | None
    weightKg: Number | None
    durationSec: Number | None
    atMs: Number | None


class Variation(Strict):
    label: str | None
    sessions: Number | None
    sets: Number | None
    lastMs: Number | None
    peak: Peak | None


class Lift(Strict):
    name: str | None
    metric: str | None
    variations: Annotated[list[Variation | None], Field(max_length=8)] | None
    variationsTruncated: bool | None


class TrendWeek(Strict):
    start: str | None
    hardSets: Number | None
    volumeKg: Number | None
    workouts: Number | None


class Trends(Strict):
    weeks: Annotated[list[TrendWeek | None], Field(max_length=12)] | None
    weeksTruncated: bool | None
    daysPerWeek: Number | None
    records30d: Number | None


class Snapshot(Success):
    freshness: Literal["absent", "fresh", "stale", "unsupported_version"]
    snapshotVersion: int | None = None
    syncedAtMs: int | None = None
    builtAtMs: int | None = None
    zone: str | None = None


class Progress(Snapshot):
    week: Week | None = None
    lifts: Annotated[list[Lift | None], Field(max_length=40)] | None = None
    liftsTruncated: bool | None = None
    trends: Trends | None = None


class SavedEntry(Strict):
    name: str | None
    sets: Number | None
    reps: Number | None
    loadKg: Number | None
    scheme: str | None
    modifiers: str | None
    supersetGroup: Number | None
    warmup: bool | None


class Preset(Strict):
    id: Number | None
    name: str | None
    entries: Annotated[list[SavedEntry], Field(max_length=50)]
    entriesTruncated: bool


class Saved(Snapshot):
    presets: Annotated[list[Preset], Field(max_length=50)] | None = None
    truncated: bool | None = None
