import { flexRender, type Table } from "@tanstack/react-table";

export function DataTable<TData>({
  table,
  empty,
  loading = false,
}: {
  table: Table<TData>;
  empty: string;
  loading?: boolean;
}) {
  const rows = table.getRowModel().rows;
  const columnCount = table.getVisibleLeafColumns().length;
  const showEmpty = rows.length === 0 || (loading && rows.length === 0);

  return (
    <div className="table-wrap">
      <table>
        <thead>
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id}>
              {group.headers.map((header) => (
                <th key={header.id}>
                  {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {showEmpty ? (
            <tr>
              <td colSpan={columnCount} className="empty">
                {empty}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={row.id} className={row.getIsSelected?.() ? "selected" : undefined}>
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
