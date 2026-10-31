type Props = {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

function Pagination({ page, totalPages, totalElements, onPageChange }: Props) {
  if (totalPages <= 1) return null

  return (
    <nav className="pagination" aria-label="Pagination">
      <button disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        ‹ Prev
      </button>
      <span>
        Page {page + 1} of {totalPages} · {totalElements} total
      </span>
      <button disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Next ›
      </button>
    </nav>
  )
}

export default Pagination
