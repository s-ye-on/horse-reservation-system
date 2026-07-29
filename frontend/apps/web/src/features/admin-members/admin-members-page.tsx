import { useEffect, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AdminMemberPageResponse, AdminMemberResponse } from '@horse/api-client'
import {
  adminMembersApi,
  getAdminMembersErrorKind,
  type AdminMembersApi,
} from './admin-members.api'
import './admin-members-page.css'

const MEMBER_LIST_QUERY_KEY = ['admin', 'member-list'] as const
const PAGE_SIZE = 20
const EMPTY_MEMBERS: AdminMemberResponse[] = []

interface AdminMembersPageProps {
  api?: AdminMembersApi
}

function getErrorMessage(error: unknown, target: 'list' | 'detail' | 'update') {
  const kind = getAdminMembersErrorKind(error)
  if (kind === 'forbidden') {
    return '관리자 권한이 없어 회원 정보를 확인할 수 없습니다.'
  }
  if (kind === 'not-found') {
    return target === 'list'
      ? '회원 정보를 찾을 수 없습니다.'
      : '선택한 회원이 더 이상 존재하지 않습니다.'
  }
  if (target === 'update') {
    return '승인 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }
  return '회원 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function MemberRideStat({ label, count }: { label: string; count?: number }) {
  return (
    <div className="admin-member-ride-stat">
      <dt>{label}</dt>
      <dd>{count ?? 0}회</dd>
    </div>
  )
}

export function AdminMembersPage({ api = adminMembersApi }: AdminMembersPageProps) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [selectedMemberId, setSelectedMemberId] = useState<number>()
  const membersQuery = useQuery({
    queryKey: [...MEMBER_LIST_QUERY_KEY, page],
    queryFn: () => api.getMembers(page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const members = membersQuery.data?.content ?? EMPTY_MEMBERS
  const totalElements = membersQuery.data?.totalElements ?? 0
  const totalPages = membersQuery.data?.totalPages ?? 0
  const activeMemberId = selectedMemberId !== undefined
    && members.some((member) => member.id === selectedMemberId)
    ? selectedMemberId
    : members[0]?.id

  useEffect(() => {
    if (totalPages === 0 && page !== 0) {
      setPage(0)
    } else if (totalPages > 0 && page >= totalPages) {
      setPage(totalPages - 1)
    }
  }, [page, totalPages])

  const memberQuery = useQuery({
    queryKey: ['admin', 'members', activeMemberId],
    queryFn: () => api.getMember(activeMemberId as number),
    enabled: activeMemberId !== undefined,
  })

  const permissionMutation = useMutation({
    mutationFn: ({ memberId, permissions }: {
      memberId: number
      permissions: { dressageApproved: boolean; jumpingApproved: boolean }
    }) => api.changeRidingPermissions(memberId, permissions),
    onSuccess: (updatedMember) => {
      queryClient.setQueriesData<AdminMemberPageResponse>({ queryKey: MEMBER_LIST_QUERY_KEY }, (current) => current ? {
        ...current,
        content: current.content.map((member) => member.id === updatedMember.id ? updatedMember : member),
      } : current)
      queryClient.setQueryData(['admin', 'members', updatedMember.id], updatedMember)
    },
  })

  const changePermission = (permission: 'dressageApproved' | 'jumpingApproved') => {
    const member = memberQuery.data
    if (member?.id === undefined || permissionMutation.isPending) {
      return
    }

    permissionMutation.reset()
    permissionMutation.mutate({
      memberId: member.id,
      permissions: {
        dressageApproved: permission === 'dressageApproved'
          ? !member.dressageApproved
          : Boolean(member.dressageApproved),
        jumpingApproved: permission === 'jumpingApproved'
          ? !member.jumpingApproved
          : Boolean(member.jumpingApproved),
      },
    })
  }

  if (membersQuery.isPending) {
    return <AdminMembersState message="회원 목록을 불러오는 중입니다." />
  }

  if (membersQuery.isError && membersQuery.data === undefined) {
    return (
      <AdminMembersState
        error
        message={getErrorMessage(membersQuery.error, 'list')}
        onRetry={() => { void membersQuery.refetch() }}
      />
    )
  }

  return (
    <main className="admin-members-page">
      <div className="admin-members-shell">
        <header className="admin-members-header">
          <div>
            <p className="admin-members-eyebrow">MEMBER OPERATIONS</p>
            <h1>회원 및 기승 승인</h1>
          </div>
          <p className="admin-members-count">전체 {totalElements}명</p>
        </header>

        {membersQuery.isError ? (
          <div className="admin-members-page-error" role="alert">
            <span>{getErrorMessage(membersQuery.error, 'list')}</span>
            <button type="button" onClick={() => { void membersQuery.refetch() }}>다시 시도</button>
          </div>
        ) : null}

        {members.length === 0 ? (
          <section className="admin-members-panel admin-members-state" aria-live="polite">
            등록된 회원이 없습니다.
          </section>
        ) : (
          <div className="admin-members-layout">
            <section className="admin-members-panel" aria-label="회원 목록" aria-busy={membersQuery.isFetching}>
              <ul className="admin-members-list">
                {members.map((member) => (
                  <li key={member.id}>
                    <button
                      className="admin-member-select"
                      type="button"
                      aria-current={member.id === activeMemberId}
                      onClick={() => setSelectedMemberId(member.id)}
                    >
                      <strong>{member.name}</strong>
                      <span>{member.phone}</span>
                    </button>
                  </li>
                ))}
              </ul>
              {totalPages > 0 ? (
                <nav className="admin-members-pagination" aria-label="회원 목록 페이지">
                  <button
                    type="button"
                    disabled={page === 0 || membersQuery.isFetching}
                    onClick={() => setPage((current) => current - 1)}
                  >이전</button>
                  <span aria-live="polite">{page + 1} / {totalPages} 페이지</span>
                  <button
                    type="button"
                    disabled={page + 1 >= totalPages || !membersQuery.data?.hasNext || membersQuery.isFetching}
                    onClick={() => setPage((current) => current + 1)}
                  >다음</button>
                </nav>
              ) : null}
              {membersQuery.isFetching ? <p className="admin-members-page-loading" role="status">페이지 이동 중입니다.</p> : null}
            </section>

            <section className="admin-members-panel" aria-label="회원 상세">
              {memberQuery.isPending ? (
                <div className="admin-members-state" aria-live="polite">회원 상세를 불러오는 중입니다.</div>
              ) : memberQuery.isError ? (
                <div className="admin-members-state admin-members-error" role="alert">
                  {getErrorMessage(memberQuery.error, 'detail')}
                </div>
              ) : memberQuery.data ? (
                <MemberDetail
                  member={memberQuery.data}
                  isUpdating={permissionMutation.isPending}
                  updateError={permissionMutation.isError
                    ? getErrorMessage(permissionMutation.error, 'update')
                    : undefined}
                  onChangePermission={changePermission}
                />
              ) : null}
            </section>
          </div>
        )}
      </div>
    </main>
  )
}

function AdminMembersState({
  message,
  error = false,
  onRetry,
}: {
  message: string
  error?: boolean
  onRetry?: () => void
}) {
  return (
    <main className="admin-members-page">
      <div className="admin-members-shell">
        <section
          className={`admin-members-panel admin-members-state${error ? ' admin-members-error' : ''}`}
          role={error ? 'alert' : undefined}
          aria-live="polite"
        >
          <p>{message}</p>
          {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
        </section>
      </div>
    </main>
  )
}

interface MemberDetailProps {
  member: AdminMemberResponse
  isUpdating: boolean
  updateError?: string
  onChangePermission(permission: 'dressageApproved' | 'jumpingApproved'): void
}

function MemberDetail({ member, isUpdating, updateError, onChangePermission }: MemberDetailProps) {
  return (
    <div className="admin-member-detail">
      <div className="admin-member-title-row">
        <div>
          <h2>{member.name}</h2>
          <p className="admin-member-phone">{member.phone}</p>
        </div>
        <span className="admin-member-arena-badge">
          {member.canUseLargeArena ? '대마장 이용 가능' : '원형마장 이용'}
        </span>
      </div>

      <dl className="admin-member-ride-grid">
        <MemberRideStat label="일반 기승" count={member.generalRideCount} />
        <MemberRideStat label="마장마술" count={member.dressageRideCount} />
        <MemberRideStat label="장애물" count={member.jumpingRideCount} />
      </dl>

      <section className="admin-member-permissions" aria-labelledby="special-permission-title">
        <h3 id="special-permission-title">특수 클래스 승인</h3>
        <p>상담과 안전 확인을 마친 회원만 승인합니다.</p>
        <PermissionToggle
          label="마장마술"
          description={member.dressageApproved ? '예약 가능' : '승인 필요'}
          checked={Boolean(member.dressageApproved)}
          disabled={isUpdating}
          onClick={() => onChangePermission('dressageApproved')}
        />
        <PermissionToggle
          label="장애물"
          description={member.jumpingApproved ? '예약 가능' : '승인 필요'}
          checked={Boolean(member.jumpingApproved)}
          disabled={isUpdating}
          onClick={() => onChangePermission('jumpingApproved')}
        />
        {updateError ? <p className="admin-member-inline-error" role="alert">{updateError}</p> : null}
      </section>
    </div>
  )
}

interface PermissionToggleProps {
  label: string
  description: string
  checked: boolean
  disabled: boolean
  onClick(): void
}

function PermissionToggle({ label, description, checked, disabled, onClick }: PermissionToggleProps) {
  return (
    <div className="admin-member-toggle-row">
      <div className="admin-member-toggle-copy">
        <strong>{label}</strong>
        <span>{description}</span>
      </div>
      <button
        className="admin-member-toggle"
        type="button"
        role="switch"
        aria-label={`${label} 승인`}
        aria-checked={checked}
        disabled={disabled}
        onClick={onClick}
      />
    </div>
  )
}
