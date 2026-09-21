/// Shared, bounded pagination contract for read-only and CRUD repositories.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PageRequest {
    /// Zero-based page index.
    pub index: u64,
    /// Number of rows requested by the caller after server-side clamping.
    pub size: u64,
}

impl PageRequest {
    pub const DEFAULT_SIZE: u64 = 20;
    pub const MAX_SIZE: u64 = 200;

    pub fn new(index: u64, size: u64) -> Self {
        Self {
            index,
            size: size.clamp(1, Self::MAX_SIZE),
        }
    }

    pub fn offset(self) -> u64 {
        self.index.saturating_mul(self.size)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SortDirection {
    Asc,
    Desc,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageResult<T> {
    pub rows: Vec<T>,
    pub total: u64,
    pub page: PageRequest,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn page_size_is_bounded_and_offset_cannot_overflow() {
        assert_eq!(PageRequest::new(2, 0).size, 1);
        assert_eq!(PageRequest::new(2, 9999).size, PageRequest::MAX_SIZE);
        assert_eq!(PageRequest::new(u64::MAX, u64::MAX).offset(), u64::MAX);
    }
}
