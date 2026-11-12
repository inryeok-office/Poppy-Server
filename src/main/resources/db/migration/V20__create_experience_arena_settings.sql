-- 체험 공간 설정(단일 행)과 변경 이력. 행이 없으면 서버는 6x6-박람회 기본값을 쓴다.
CREATE TABLE experience_arena_settings (
    id SMALLINT PRIMARY KEY,
    preset_code TEXT,
    width_m DOUBLE PRECISION NOT NULL,
    depth_m DOUBLE PRECISION NOT NULL,
    margin_m DOUBLE PRECISION NOT NULL,
    start_x_m DOUBLE PRECISION NOT NULL,
    start_y_m DOUBLE PRECISION NOT NULL,
    start_heading VARCHAR(2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by TEXT NOT NULL,
    CONSTRAINT ck_experience_arena_settings_singleton CHECK (id = 1)
);

CREATE TABLE experience_arena_setting_changes (
    id UUID PRIMARY KEY,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by TEXT NOT NULL,
    previous_settings TEXT NOT NULL,
    new_settings TEXT NOT NULL
);

CREATE INDEX idx_experience_arena_setting_changes_changed_at ON experience_arena_setting_changes (changed_at DESC);
