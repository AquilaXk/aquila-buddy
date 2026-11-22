//! Remembered window geometry: an 18-byte little-endian binary record stored
//! under the desktop app local data directory so size, position, and maximized
//! state restore safely across launches without third-party plugins.

use std::fs;
use std::path::Path;

const FILE_NAME: &str = "window-state.bin";
const MAGIC: [u8; 4] = *b"ABWS";
const VERSION: u8 = 1;
pub const RECORD_LEN: usize = 18;
const FLAG_MAXIMIZED: u8 = 0b0000_0001;
const KNOWN_FLAGS: u8 = FLAG_MAXIMIZED;
const MIN_WIDTH: u16 = 480;
const MIN_HEIGHT: u16 = 360;
const VISIBLE_GRIP: (i32, i32) = (120, 40);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct WindowGeometry {
    pub width: u16,
    pub height: u16,
    pub x: i32,
    pub y: i32,
    pub maximized: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MonitorRect {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

impl MonitorRect {
    fn contains(self, px: i32, py: i32) -> bool {
        let (Some(right), Some(bottom)) = (
            self.x.checked_add(self.width),
            self.y.checked_add(self.height),
        ) else {
            return false;
        };
        px >= self.x && px < right && py >= self.y && py < bottom
    }
}

fn read_u16(bytes: &[u8], at: usize) -> Option<u16> {
    let slice = bytes.get(at..at.checked_add(2)?)?;
    Some(u16::from_le_bytes([slice[0], slice[1]]))
}

fn read_i32(bytes: &[u8], at: usize) -> Option<i32> {
    let slice = bytes.get(at..at.checked_add(4)?)?;
    Some(i32::from_le_bytes([slice[0], slice[1], slice[2], slice[3]]))
}

impl WindowGeometry {
    pub fn from_logical(size: (f64, f64), origin: (f64, f64), maximized: bool) -> Option<Self> {
        let clamp_edge = |val: f64, min_edge: u16| {
            let rounded = val.round();
            (rounded.is_finite()
                && rounded >= f64::from(min_edge)
                && rounded <= f64::from(u16::MAX))
            .then_some(rounded as u16)
        };
        let coord = |val: f64| {
            let rounded = val.round();
            (rounded.is_finite()
                && rounded >= f64::from(i32::MIN)
                && rounded <= f64::from(i32::MAX))
            .then_some(rounded as i32)
        };
        Some(Self {
            width: clamp_edge(size.0, MIN_WIDTH)?,
            height: clamp_edge(size.1, MIN_HEIGHT)?,
            x: coord(origin.0)?,
            y: coord(origin.1)?,
            maximized,
        })
    }

    pub fn encode(self) -> [u8; RECORD_LEN] {
        let mut out = [0u8; RECORD_LEN];
        out[..4].copy_from_slice(&MAGIC);
        out[4] = VERSION;
        out[5] = if self.maximized { FLAG_MAXIMIZED } else { 0 };
        out[6..8].copy_from_slice(&self.width.to_le_bytes());
        out[8..10].copy_from_slice(&self.height.to_le_bytes());
        out[10..14].copy_from_slice(&self.x.to_le_bytes());
        out[14..18].copy_from_slice(&self.y.to_le_bytes());
        out
    }

    pub fn decode(bytes: &[u8]) -> Option<Self> {
        if bytes.len() != RECORD_LEN || bytes[..4] != MAGIC || bytes[4] != VERSION {
            return None;
        }
        let flags = bytes[5];
        if flags & !KNOWN_FLAGS != 0 {
            return None;
        }
        let width = read_u16(bytes, 6)?;
        let height = read_u16(bytes, 8)?;
        if width < MIN_WIDTH || height < MIN_HEIGHT {
            return None;
        }
        Some(Self {
            width,
            height,
            x: read_i32(bytes, 10)?,
            y: read_i32(bytes, 14)?,
            maximized: flags & FLAG_MAXIMIZED != 0,
        })
    }

    pub fn visible_origin(self, monitors: &[MonitorRect]) -> Option<(f64, f64)> {
        let grip_x = self.x.checked_add(VISIBLE_GRIP.0)?;
        let grip_y = self.y.checked_add(VISIBLE_GRIP.1)?;
        monitors
            .iter()
            .any(|m| m.contains(self.x, self.y) && m.contains(grip_x, grip_y))
            .then(|| (f64::from(self.x), f64::from(self.y)))
    }

    pub fn load(dir: &Path) -> Option<Self> {
        Self::decode(&fs::read(dir.join(FILE_NAME)).ok()?)
    }

    pub fn save(self, dir: &Path) -> std::io::Result<()> {
        fs::create_dir_all(dir)?;
        fs::write(dir.join(FILE_NAME), self.encode())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: WindowGeometry = WindowGeometry {
        width: 1440,
        height: 900,
        x: -1600,
        y: 24,
        maximized: true,
    };

    #[test]
    fn record_is_18_bytes_and_round_trips() {
        let bytes = SAMPLE.encode();
        assert_eq!(&bytes[..6], b"ABWS\x01\x01");
        assert_eq!(WindowGeometry::decode(&bytes), Some(SAMPLE));
    }

    #[test]
    fn damaged_records_fail_closed() {
        let good = SAMPLE.encode();
        assert_eq!(WindowGeometry::decode(&good[..17]), None);
        let mut long = good.to_vec();
        long.push(0);
        assert_eq!(WindowGeometry::decode(&long), None);
        for (at, value) in [(0, b'X'), (4, 2), (5, 0b10)] {
            let mut bad = good;
            bad[at] = value;
            assert_eq!(WindowGeometry::decode(&bad), None);
        }
        let mut tiny = good;
        tiny[6..8].copy_from_slice(&200u16.to_le_bytes());
        assert_eq!(WindowGeometry::decode(&tiny), None);
    }

    #[test]
    fn visible_origin_checks_monitors_and_overflow() {
        let primary = MonitorRect {
            x: 0,
            y: 0,
            width: 1920,
            height: 1080,
        };
        let left = MonitorRect {
            x: -1920,
            y: 0,
            width: 1920,
            height: 1080,
        };
        assert_eq!(
            SAMPLE.visible_origin(&[primary, left]),
            Some((-1600.0, 24.0))
        );
        assert_eq!(SAMPLE.visible_origin(&[primary]), None);
        let off_edge = WindowGeometry { x: 1850, ..SAMPLE };
        assert_eq!(off_edge.visible_origin(&[primary]), None);
        let overflow = WindowGeometry {
            x: i32::MAX,
            ..SAMPLE
        };
        assert_eq!(overflow.visible_origin(&[primary]), None);
    }

    #[test]
    fn state_persists_through_data_dir() {
        let dir = std::env::temp_dir().join(format!("aquila-buddy-wstate-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        assert_eq!(WindowGeometry::load(&dir), None);
        SAMPLE.save(&dir).unwrap();
        assert_eq!(WindowGeometry::load(&dir), Some(SAMPLE));
        fs::write(dir.join(FILE_NAME), b"corrupt").unwrap();
        assert_eq!(WindowGeometry::load(&dir), None);
        let _ = fs::remove_dir_all(&dir);
    }
}
